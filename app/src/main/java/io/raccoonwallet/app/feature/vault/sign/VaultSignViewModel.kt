package io.raccoonwallet.app.feature.vault.sign

import android.app.Activity
import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.raccoonwallet.app.core.crypto.EthSigner
import io.raccoonwallet.app.core.crypto.EthTransaction
import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.crypto.LindellSign
import io.raccoonwallet.app.core.crypto.Secp256k1
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.FlowError
import io.raccoonwallet.app.core.model.SignState
import io.raccoonwallet.app.core.model.TransactionRecord
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.model.TxDisplayData
import io.raccoonwallet.app.core.network.BroadcastException
import io.raccoonwallet.app.core.model.TxStatus
import io.raccoonwallet.app.core.storage.BiometricGate
import io.raccoonwallet.app.core.transport.TransportMessage
import io.raccoonwallet.app.core.transport.nfc.RaccoonWalletHceService
import io.raccoonwallet.app.core.transport.qr.QrTransport
import io.raccoonwallet.app.deps
import io.raccoonwallet.app.nav.VaultSign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.util.UUID

class VaultSignViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val app = application.deps
    private val publicStore = app.publicStore
    private val chainManager = app.chainManager
    private val transportBridge = app.transportBridge

    private val _signState = MutableStateFlow<SignState>(SignState.Idle)
    val signState: StateFlow<SignState> = _signState.asStateFlow()

    var transportMode: TransportMode = TransportMode.NFC
        private set

    // Transient signing state
    private var ethTransaction: EthTransaction? = null
    private var txHash: ByteArray? = null
    private var k1: BigInteger? = null
    private var signRequest: TransportMessage.SignRequest? = null
    private var accountIndex: Int = 0
    private var chainId: Long = 1L

    init {
        startSign(savedStateHandle.toRoute<VaultSign>())
    }

    private fun startSign(route: VaultSign) {
        accountIndex = route.accountIndex
        chainId = route.chainId
        viewModelScope.launch {
            try {
                _signState.value = SignState.BuildingTransaction

                val tx = withContext(Dispatchers.Default) {
                    EthSigner.buildTransaction(
                        nonce = BigInteger.valueOf(route.nonce),
                        chainId = route.chainId,
                        maxPriorityFeePerGas = BigInteger(route.maxPriorityFeePerGas),
                        maxFeePerGas = BigInteger(route.maxFeePerGas),
                        gasLimit = BigInteger.valueOf(route.gasLimit),
                        to = route.to,
                        value = BigInteger(route.valueWei),
                        data = if (route.data != "0x") Hex.decode(route.data.removePrefix("0x")) else byteArrayOf()
                    )
                }
                ethTransaction = tx

                val hash = withContext(Dispatchers.Default) { tx.signingHash() }
                txHash = hash

                val prepare = withContext(Dispatchers.Default) { LindellSign.vaultPrepare(hash) }
                k1 = prepare.k1

                val chain = ChainRegistry.byChainId(route.chainId) ?: ChainRegistry.ETHEREUM
                val feeWei = BigInteger.valueOf(route.gasLimit).multiply(BigInteger(route.maxFeePerGas))
                val feeFormatted = "${Hex.weiToEther(feeWei)} ${chain.symbol}"

                val displayData = TxDisplayData(
                    chainName = chain.name,
                    fromAddress = publicStore.getAccounts().getOrNull(route.accountIndex)?.address ?: "",
                    toAddress = route.to,
                    value = if (route.data != "0x") "Token Transfer" else "${Hex.weiToEther(BigInteger(route.valueWei))} ${chain.symbol}",
                    data = route.data,
                    estimatedFee = feeFormatted
                )

                val sessionId = UUID.randomUUID().toString()
                signRequest = TransportMessage.SignRequest(
                    sessionId = sessionId,
                    accountIndex = route.accountIndex,
                    chainId = route.chainId,
                    txHash = hash,
                    r1Point = Secp256k1.compressPoint(prepare.R1),
                    displayData = displayData
                )

                _signState.value = SignState.ChoosingTransport
            } catch (e: Exception) {
                _signState.value = SignState.Failed(FlowError.CryptoFailed(
                    "Failed to build transaction: ${e.message}"
                ))
            }
        }
    }

    fun selectTransport(mode: TransportMode) {
        transportMode = mode
        val request = signRequest ?: return

        when (mode) {
            TransportMode.NFC -> _signState.value = SignState.AwaitingTap1
            TransportMode.QR -> {
                val frames = transportBridge.encodeForQr(request)
                _signState.value = SignState.DisplayingQr(frames)
            }
        }
    }

    // ── NFC Flow ──

    fun doNfcTap1(activity: Activity) {
        val request = signRequest ?: return
        viewModelScope.launch {
            try {
                _signState.value = SignState.AwaitingTap1
                transportBridge.nfcSendAndDisconnect(
                    activity,
                    RaccoonWalletHceService.INS_SIGN_REQUEST,
                    request
                )
                _signState.value = SignState.WaitingForApproval
            } catch (e: Exception) {
                _signState.value = SignState.Failed(FlowError.TransferInterrupted(
                    detail = "Tap 1: ${e.message}"
                ))
            }
        }
    }

    fun doNfcTap2(activity: Activity) {
        viewModelScope.launch {
            try {
                _signState.value = SignState.AwaitingTap2
                val response = transportBridge.nfcPollAndDisconnect(activity, timeoutMs = 30_000)
                if (response == null) {
                    _signState.value = SignState.Failed(FlowError.DeviceNotReady(
                        detail = "Signer hasn't approved yet"
                    ))
                    return@launch
                }
                if (response is TransportMessage.SignResponse) {
                    processSignResponse(response, activity as? FragmentActivity)
                } else {
                    _signState.value = SignState.Failed(FlowError.ProtocolMismatch(
                        detail = "Expected signature response"
                    ))
                }
            } catch (e: Exception) {
                _signState.value = SignState.Failed(FlowError.TransferInterrupted(
                    detail = "Tap 2: ${e.message}"
                ))
            }
        }
    }

    // ── QR Flow ──

    fun onQrDisplayDone() {
        _signState.value = SignState.ScanningQr(0f)
        transportBridge.resetQrDecoder()
    }

    fun onQrFrameScanned(raw: String, activity: FragmentActivity? = null) {
        if (_signState.value !is SignState.ScanningQr) return
        if (!raw.startsWith("FW/")) return

        when (val result = transportBridge.decodeQrFrame(raw)) {
            is QrTransport.ScanResult.Progress -> {
                _signState.value = SignState.ScanningQr(result.received.toFloat() / result.total)
            }
            is QrTransport.ScanResult.MessageReady -> {
                if (result.message is TransportMessage.SignResponse) {
                    processSignResponse(result.message, activity)
                } else {
                    _signState.value = SignState.Failed(FlowError.ProtocolMismatch(
                        detail = "Expected signature response"
                    ))
                }
            }
            is QrTransport.ScanResult.Error -> {
                _signState.value = SignState.Failed(FlowError.QrScanFailed(result.message))
            }
        }
    }

    // ── Finalization ──

    private fun processSignResponse(response: TransportMessage.SignResponse, activity: FragmentActivity? = null) {
        if (!response.approved) {
            _signState.value = SignState.Rejected("Transaction rejected by Signer")
            return
        }

        viewModelScope.launch {
            try {
                _signState.value = SignState.Finalizing

                val authMode = publicStore.getAuthMode()
                if (authMode != AuthMode.NONE) {
                    val act = activity
                        ?: throw RuntimeException("Activity not available for biometric prompt")
                    val authed = BiometricGate.authenticate(act, authMode)
                    if (!authed) {
                        _signState.value = SignState.Failed(FlowError.BiometricDenied())
                        return@launch
                    }
                }
                val secretStore = app.getSecretStore(authMode)

                val paillierSk = secretStore.getPaillierSecretKey()
                    ?: throw RuntimeException("Paillier secret key not found")
                val paillierPk = secretStore.getPaillierPublicKey()
                    ?: throw RuntimeException("Paillier public key not found")
                val jointPubKey = secretStore.getJointPublicKey(accountIndex)
                    ?: throw RuntimeException("Joint public key not found")

                val r2 = Secp256k1.decompressPoint(response.r2Point)
                val cPartial = BigInteger(response.cPartial)
                val signerR = BigInteger(response.signerR)
                val localK1 = k1 ?: throw RuntimeException("k1 not found")
                val localTxHash = txHash ?: throw RuntimeException("txHash not found")
                val localTx = ethTransaction ?: throw RuntimeException("Transaction not found")

                val signature = withContext(Dispatchers.Default) {
                    LindellSign.vaultFinalize(
                        k1 = localK1,
                        R2 = r2,
                        cPartial = cPartial,
                        signerR = signerR,
                        paillierSk = paillierSk,
                        paillierPk = paillierPk,
                        txHash = localTxHash,
                        expectedPubKey = jointPubKey
                    )
                }

                val signedHex = localTx.encodeSignedHex(signature)

                val broadcastHash = withContext(Dispatchers.IO) {
                    chainManager.getClient(chainId).sendRawTransaction(signedHex)
                }

                publicStore.addTransactionRecord(
                    TransactionRecord(
                        hash = broadcastHash,
                        chainId = chainId,
                        from = signRequest?.displayData?.fromAddress ?: "",
                        to = signRequest?.displayData?.toAddress ?: "",
                        value = signRequest?.displayData?.value ?: "",
                        timestamp = System.currentTimeMillis(),
                        status = TxStatus.PENDING
                    )
                )

                clearTransientState()
                _signState.value = SignState.Complete(broadcastHash)
            } catch (e: BroadcastException) {
                _signState.value = SignState.Failed(
                    FlowError.BroadcastFailed(e.message ?: "Unknown broadcast error")
                )
            } catch (e: Exception) {
                _signState.value = SignState.Failed(
                    FlowError.CryptoFailed("Signing failed: ${e.message}")
                )
            }
        }
    }

    private fun clearTransientState() {
        k1 = null
        txHash = null
        ethTransaction = null
        signRequest = null
    }

    override fun onCleared() {
        clearTransientState()
        super.onCleared()
    }

}
