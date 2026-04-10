package io.raccoonwallet.app.feature.setup.dkg

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.raccoonwallet.app.nav.SetupDkg
import io.raccoonwallet.app.core.crypto.Bip32
import io.raccoonwallet.app.core.crypto.Bip39
import io.raccoonwallet.app.core.crypto.LindellDkg
import io.raccoonwallet.app.core.crypto.PaillierCipher
import io.raccoonwallet.app.core.crypto.Secp256k1
import io.raccoonwallet.app.core.model.Account
import io.raccoonwallet.app.core.model.AppMode
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.model.DkgState
import io.raccoonwallet.app.core.model.FlowError
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.storage.KeystoreCipher
import io.raccoonwallet.app.core.storage.SecretStore
import io.raccoonwallet.app.core.transport.TransportMessage
import io.raccoonwallet.app.deps
import android.app.Activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger

/**
 * DKG ViewModel.
 *
 * The SIGNER generates the seed, derives keys, splits them, and sends
 * the Vault's portion. This is more secure because:
 * - The Signer is air-gapped — no malware can steal the full key during setup
 * - The Vault (online phone) never sees the full private key
 *
 * After setup:
 *   Vault holds: x1 + Paillier sk → finalizes signatures
 *   Signer holds: x2 + ckey + Paillier pk → computes partial sigs
 */
class DkgViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    companion object {
        const val ACCOUNT_COUNT = 8
        private const val ACK_TIMEOUT_MS = 120_000L // 2 minutes
    }

    private val app = application.deps
    private val publicStore = app.publicStore
    private val transportBridge = app.transportBridge
    private val nfcReaderTransport = app.nfcReaderTransport
    private val hceSessionManager = app.hceSessionManager

    // Created once in proceedToStore() after the user's biometric choice is known
    private var secretStore: SecretStore? = null

    private val _dkgState = MutableStateFlow<DkgState>(DkgState.Idle)
    val dkgState: StateFlow<DkgState> = _dkgState.asStateFlow()

    val isVaultMode: Boolean = savedStateHandle.toRoute<SetupDkg>().isVault

    init {
        if (isVaultMode) startVaultDkg() else startSignerDkg()
    }

    var transportMode: TransportMode = TransportMode.NFC
        private set

    var authMode: AuthMode = AuthMode.NONE
        private set

    // Job tracking for the NFC ACK collector (Signer side)
    private var ackCollectorJob: kotlinx.coroutines.Job? = null

    // Signer-side transient data (wiped after DKG)
    private var mnemonic: List<String>? = null
    private var paillierKeyPair: PaillierCipher.KeyPair? = null
    private var splitResults: List<LindellDkg.SplitResult>? = null

    // ══════════════════════════════════════
    // ── Signer Flow (generates seed) ──
    // ══════════════════════════════════════

    fun startSignerDkg() {
        preparedFreshStores = false
        secretStore = null
        viewModelScope.launch {
            try {
                val words = withContext(Dispatchers.Default) { Bip39.generateMnemonic() }
                mnemonic = words
                _dkgState.value = DkgState.ShowingSeed(words)
            } catch (e: Exception) {
                _dkgState.value = DkgState.Failed(FlowError.CryptoFailed(
                    e.message ?: "Failed to generate mnemonic"
                ))
            }
        }
    }

    fun showImportSeed() {
        _dkgState.value = DkgState.ImportingSeed
    }

    fun startSignerImport(words: List<String>) {
        viewModelScope.launch {
            try {
                require(Bip39.validateMnemonic(words)) { "Invalid mnemonic" }
                mnemonic = words
                onSeedConfirmed()
            } catch (e: Exception) {
                _dkgState.value = DkgState.Failed(FlowError.CryptoFailed(
                    e.message ?: "Invalid mnemonic"
                ))
            }
        }
    }

    fun onSeedConfirmed() {
        // Capture words and immediately clear the class field
        val words = mnemonic ?: return
        mnemonic = null

        viewModelScope.launch {
            try {
                _dkgState.value = DkgState.GeneratingPaillier
                val kp = withContext(Dispatchers.Default) {
                    PaillierCipher.generateKeyPair(2048)
                }
                paillierKeyPair = kp

                _dkgState.value = DkgState.SplittingKeys
                val results = withContext(Dispatchers.Default) {
                    val seed = Bip39.mnemonicToSeed(words)
                    val privateKeys = Bip32.deriveEthAccounts(seed, ACCOUNT_COUNT)
                    seed.fill(0)
                    LindellDkg.splitAllFromSeed(privateKeys, kp.publicKey)
                }

                splitResults = results
                _dkgState.value = DkgState.ChoosingBiometric
            } catch (e: Exception) {
                _dkgState.value = DkgState.Failed(FlowError.CryptoFailed(
                    e.message ?: "Key generation failed"
                ))
            }
        }
    }

    fun setAuthMode(mode: AuthMode) {
        authMode = mode
        viewModelScope.launch {
            publicStore.setAuthMode(mode)
        }
        // Signer goes to transport picker, Vault goes to waiting
        if (isVaultMode) {
            _dkgState.value = DkgState.WaitingForVault
        } else {
            _dkgState.value = DkgState.ChoosingTransport
        }
    }

    fun selectTransport(mode: TransportMode) {
        transportMode = mode
        val results = splitResults ?: return

        when (mode) {
            TransportMode.QR -> startQrDistribution(results)
            TransportMode.NFC -> startNfcDistribution(results)
        }
    }

    private fun startQrDistribution(results: List<LindellDkg.SplitResult>) {
        val kp = paillierKeyPair ?: return
        val vaultBundle = buildVaultBundle(results, kp)
        val frames = transportBridge.encodeForQr(vaultBundle)
        _dkgState.value = DkgState.DisplayingQr(frames)
    }

    private fun startNfcDistribution(results: List<LindellDkg.SplitResult>) {
        val kp = paillierKeyPair ?: return
        val vaultBundle = buildVaultBundle(results, kp)
        val encoded = app.messageCodec.encode(vaultBundle)

        // Pre-load bundle into HCE session manager for Vault to pull
        hceSessionManager.setDkgBundle(encoded)
        _dkgState.value = DkgState.AwaitingNfcTap

        // Listen for ACK from Vault with timeout
        ackCollectorJob?.cancel()
        ackCollectorJob = viewModelScope.launch {
            val ackReceived = withTimeoutOrNull(ACK_TIMEOUT_MS) {
                hceSessionManager.dkgAckFlow.first { it }
            }
            if (ackReceived != null) {
                hceSessionManager.clearDkgState()
                proceedToStore()
            } else {
                hceSessionManager.clearDkgState()
                _dkgState.value = DkgState.Failed(FlowError.Timeout(
                    phase = "waiting for Vault acknowledgment"
                ))
            }
        }
    }

    fun onQrDisplayDone() {
        _dkgState.value = DkgState.ScanningQr(0f)
        transportBridge.resetQrDecoder()
    }

    fun onVaultAckScanned(raw: String) {
        if (_dkgState.value !is DkgState.ScanningQr) return
        if (!raw.startsWith("FW/")) return
        val result = transportBridge.decodeQrFrame(raw)
        if (result is io.raccoonwallet.app.core.transport.qr.QrTransport.ScanResult.MessageReady) {
            val msg = result.message
            if (msg is TransportMessage.DkgFinalize && msg.ack) {
                proceedToStore()
            } else {
                _dkgState.value = DkgState.Failed(FlowError.ProtocolMismatch(
                    detail = "Expected DKG acknowledgment"
                ))
            }
        }
    }

    /** Route to biometric prompt or directly to storage based on user's auth mode */
    private fun proceedToStore() {
        if (authMode != AuthMode.NONE) {
            _dkgState.value = DkgState.AwaitingBiometric
        } else {
            onBiometricSuccess()
        }
    }

    fun onBiometricFailed(reason: String) {
        _dkgState.value = DkgState.Failed(FlowError.BiometricDenied(detail = reason))
    }

    /** Called by the screen after BiometricGate.authenticate() succeeds. */
    fun onBiometricSuccess() {
        viewModelScope.launch {
            prepareFreshStoresForDkg()
            if (secretStore == null) {
                secretStore = app.getSecretStore(authMode)
            }
            if (pendingVaultBundle != null) {
                doStoreVaultBundle()
            } else {
                doStoreSignerShares()
            }
        }
    }

    private suspend fun prepareFreshStoresForDkg() {
        if (preparedFreshStores) return
        secretStore = null
        try {
            KeystoreCipher.deleteKey("raccoonwallet_secret_master")
        } catch (_: Exception) {
            // Best effort
        }
        publicStore.deleteAll()
        publicStore.setAuthMode(authMode)
        preparedFreshStores = true
    }

    private fun doStoreSignerShares() {
        viewModelScope.launch {
            _dkgState.value = DkgState.StoringKeys
            val results = splitResults ?: return@launch
            val kp = paillierKeyPair ?: return@launch
            val store = secretStore ?: return@launch

            try {
                store.setSignerPaillierPk(kp.publicKey)

                val accounts = results.mapIndexed { i, split ->
                    store.setKeyShare(i, split.x2)
                    store.setJointPublicKey(i, split.jointPublicKey)
                    store.setCkey(i, split.ckey)
                    Account(
                        index = i,
                        address = split.address,
                        publicKeyCompressed = Base64.encodeToString(
                            Secp256k1.compressPoint(split.jointPublicKey), Base64.NO_WRAP
                        )
                    )
                }

                publicStore.completeDkg(accounts, AppMode.SIGNER)

                clearTransientCryptoState()
                _dkgState.value = DkgState.Complete
            } catch (e: Exception) {
                _dkgState.value = DkgState.Failed(FlowError.StorageFailed(
                    e.message ?: "Failed to store key shares"
                ))
            }
        }
    }

    // ══════════════════════════════════════
    // ── Vault Flow (receives shares) ──
    // ══════════════════════════════════════

    fun startVaultNfcReceive(activity: Activity) {
        viewModelScope.launch {
            try {
                _dkgState.value = DkgState.AwaitingNfcTap

                val connected = nfcReaderTransport.waitForTag(activity)
                if (!connected) {
                    nfcReaderTransport.disconnect()
                    _dkgState.value = DkgState.Failed(FlowError.ConnectionFailed())
                    return@launch
                }

                _dkgState.value = DkgState.ReceivingNfc()

                val message = withContext(Dispatchers.IO) {
                    nfcReaderTransport.fetchDkgBundle { progress ->
                        _dkgState.value = DkgState.ReceivingNfc(progress)
                    }
                }

                // Send ACK immediately while phones are still together.
                try {
                    withContext(Dispatchers.IO) { nfcReaderTransport.sendDkgAck() }
                } catch (_: Exception) {
                    // ACK best-effort — Vault has the data regardless
                }
                nfcReaderTransport.disconnect()

                processVaultBundle(message)
            } catch (e: Exception) {
                nfcReaderTransport.disconnect()
                _dkgState.value = DkgState.Failed(FlowError.TransferInterrupted(
                    detail = e.message
                ))
            }
        }
    }

    fun startVaultDkg() {
        preparedFreshStores = false
        secretStore = null
        _dkgState.value = DkgState.ChoosingBiometric
    }

    fun retryTransport() {
        ackCollectorJob?.cancel()
        ackCollectorJob = null
        transportBridge.cleanup()
        hceSessionManager.clearAll()
        if (isVaultMode) {
            _dkgState.value = DkgState.WaitingForVault
        } else {
            _dkgState.value = DkgState.ChoosingTransport
        }
    }

    fun startVaultQrScan() {
        _dkgState.value = DkgState.ScanningQr(0f)
        transportBridge.resetQrDecoder()
    }

    fun onVaultQrFrameScanned(raw: String) {
        if (_dkgState.value !is DkgState.ScanningQr) return
        if (!raw.startsWith("FW/")) return

        when (val result = transportBridge.decodeQrFrame(raw)) {
            is io.raccoonwallet.app.core.transport.qr.QrTransport.ScanResult.Progress -> {
                _dkgState.value = DkgState.ScanningQr(
                    result.received.toFloat() / result.total
                )
            }
            is io.raccoonwallet.app.core.transport.qr.QrTransport.ScanResult.MessageReady -> {
                processVaultBundle(result.message)
            }
            is io.raccoonwallet.app.core.transport.qr.QrTransport.ScanResult.Error -> {
                _dkgState.value = DkgState.Failed(FlowError.QrScanFailed(result.message))
            }
        }
    }

    // Holds the received bundle until biometric auth completes
    private var pendingVaultBundle: TransportMessage.DkgRound2? = null
    private var preparedFreshStores: Boolean = false

    private fun processVaultBundle(message: TransportMessage) {
        if (message is TransportMessage.DkgRound2) {
            pendingVaultBundle = message
            proceedToStore()
        } else {
            _dkgState.value = DkgState.Failed(FlowError.ProtocolMismatch(
                detail = "Expected DKG bundle"
            ))
        }
    }

    private fun doStoreVaultBundle() {
        viewModelScope.launch {
            try {
                _dkgState.value = DkgState.StoringKeys
                val message = pendingVaultBundle ?: return@launch
                val store = secretStore ?: return@launch
                require(message.q1Points.size >= 4) { "Malformed bundle" }

                val lambda = BigInteger(message.q1Points[0])
                val mu = BigInteger(message.q1Points[1])
                val paillierN = BigInteger(message.q1Points[2])
                val paillierG = BigInteger(message.q1Points[3])

                val paillierSk = PaillierCipher.SecretKey(lambda = lambda, mu = mu)
                val paillierPk = PaillierCipher.PublicKey(
                    n = paillierN, nSquared = paillierN.multiply(paillierN), g = paillierG
                )

                store.setPaillierPublicKey(paillierPk)
                store.setPaillierSecretKey(paillierSk)

                val count = message.q1Points.size - 4
                require(count > 0) { "No accounts in bundle" }
                require(message.ckeys.size >= count) { "Bundle truncated: ${message.ckeys.size} keys for $count accounts" }
                val accounts = (0 until count).map { i ->
                    val Q = Secp256k1.decompressPoint(message.q1Points[i + 4])
                    require(Secp256k1.isOnCurve(Q)) { "Joint public key $i not on curve" }
                    val x1 = BigInteger(message.ckeys[i])
                    val address = Secp256k1.pointToEthAddress(Q)

                    store.setKeyShare(i, x1)
                    store.setJointPublicKey(i, Q)

                    Account(
                        index = i,
                        address = address,
                        publicKeyCompressed = Base64.encodeToString(
                            message.q1Points[i + 4], Base64.NO_WRAP
                        )
                    )
                }

                publicStore.completeDkg(accounts, AppMode.VAULT)
                pendingVaultBundle = null

                if (transportMode == TransportMode.NFC) {
                    _dkgState.value = DkgState.Complete
                } else {
                    val ackMessage = TransportMessage.DkgFinalize(ack = true)
                    val ackFrames = transportBridge.encodeForQr(ackMessage)
                    _dkgState.value = DkgState.DisplayingAck(ackFrames)
                }
            } catch (e: Exception) {
                _dkgState.value = DkgState.Failed(FlowError.StorageFailed(
                    e.message ?: "Failed to process shares"
                ))
            }
        }
    }

    fun onVaultConfirmed() {
        _dkgState.value = DkgState.Complete
    }

    // ══════════════════════════════════════
    // ── Cancel / Back ──
    // ══════════════════════════════════════

    fun cancel() {
        ackCollectorJob?.cancel()
        ackCollectorJob = null
        clearTransientCryptoState()
        pendingVaultBundle = null
        preparedFreshStores = false
        authMode = AuthMode.NONE
        transportBridge.cleanup()
        hceSessionManager.clearAll()
        _dkgState.value = DkgState.Idle
        viewModelScope.launch {
            try { secretStore?.deleteAll() } catch (_: Exception) {}
            secretStore = null
            publicStore.deleteAll()
            try {
                KeystoreCipher.deleteKey("raccoonwallet_secret_master")
            } catch (_: Exception) {
                // Best effort
            }
        }
    }

    override fun onCleared() {
        ackCollectorJob?.cancel()
        clearTransientCryptoState()
        pendingVaultBundle = null
        hceSessionManager.clearDkgState()
        super.onCleared()
    }

    // ══════════════════════════════════════
    // ── Helpers ──
    // ══════════════════════════════════════

    private fun clearTransientCryptoState() {
        mnemonic = null
        paillierKeyPair = null
        splitResults = null
    }

    /**
     * Build the bundle that Signer sends to Vault.
     * Contains: Paillier sk + pk + joint public keys + x1 shares.
     */
    private fun buildVaultBundle(
        results: List<LindellDkg.SplitResult>,
        kp: PaillierCipher.KeyPair
    ): TransportMessage {
        val q1Points = mutableListOf<ByteArray>()
        q1Points.add(kp.secretKey.lambda.toByteArray())
        q1Points.add(kp.secretKey.mu.toByteArray())
        q1Points.add(kp.publicKey.n.toByteArray())
        q1Points.add(kp.publicKey.g.toByteArray())
        results.forEach { q1Points.add(Secp256k1.compressPoint(it.jointPublicKey)) }

        val ckeys = mutableListOf<ByteArray>()
        results.forEach { ckeys.add(it.x1.toByteArray()) }

        return TransportMessage.DkgRound2(q1Points = q1Points, ckeys = ckeys)
    }
}
