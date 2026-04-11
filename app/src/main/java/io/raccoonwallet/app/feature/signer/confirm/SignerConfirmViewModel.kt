package io.raccoonwallet.app.feature.signer.confirm

import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.raccoonwallet.app.nav.SignerConfirm
import io.raccoonwallet.app.core.crypto.EthSigner
import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.crypto.LindellSign
import io.raccoonwallet.app.core.crypto.Secp256k1
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.model.FlowError
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.model.TxDisplayData
import io.raccoonwallet.app.core.transport.TransportMessage
import io.raccoonwallet.app.core.storage.BiometricGate
import io.raccoonwallet.app.deps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

sealed class SignerConfirmState {
    data object Loading : SignerConfirmState()
    data class ShowingRequest(val displayData: TxDisplayData) : SignerConfirmState()
    data object Computing : SignerConfirmState()
    data class ResponseReady(val transportMode: TransportMode) : SignerConfirmState()
    data class DisplayingQr(val frames: List<String>) : SignerConfirmState()
    data object Done : SignerConfirmState()
    data class Failed(val error: FlowError) : SignerConfirmState()
}

class SignerConfirmViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val app = application.deps
    private val publicStore = app.publicStore
    private val transportBridge = app.transportBridge
    private val hceSessionManager = app.hceSessionManager

    private val route = savedStateHandle.toRoute<SignerConfirm>()
    private val sessionId: String = route.sessionId
    private val transportMode: TransportMode = route.transportMode

    private val _state = MutableStateFlow<SignerConfirmState>(SignerConfirmState.Loading)
    val state: StateFlow<SignerConfirmState> = _state.asStateFlow()

    private var signRequest: TransportMessage.SignRequest? = null

    init {
        viewModelScope.launch {
            try {
                val pending = hceSessionManager.signRequestFlow.first { it.request.sessionId == sessionId }
                signRequest = pending.request
                _state.value = SignerConfirmState.ShowingRequest(pending.request.displayData)
            } catch (e: Exception) {
                _state.value = SignerConfirmState.Failed(FlowError.ProtocolMismatch(
                    detail = "Failed to load request: ${e.message}"
                ))
            }
        }
    }

    fun approve(activity: FragmentActivity? = null) {
        val request = signRequest ?: return
        viewModelScope.launch {
            try {
                _state.value = SignerConfirmState.Computing

                val authMode = publicStore.getAuthMode()
                if (authMode != AuthMode.NONE) {
                    val act = activity
                        ?: throw RuntimeException("Activity not available for biometric prompt")
                    val authed = BiometricGate.authenticate(act, authMode)
                    if (!authed) {
                        _state.value = SignerConfirmState.Failed(FlowError.BiometricDenied())
                        return@launch
                    }
                }
                val secretStore = app.getSecretStore(authMode)

                val x2 = secretStore.getKeyShare(request.accountIndex)
                    ?: throw KeyShareNotFoundException("Key share")
                val ckey = secretStore.getCkey(request.accountIndex)
                    ?: throw KeyShareNotFoundException("Ckey")
                val paillierPk = secretStore.getSignerPaillierPk()
                    ?: throw KeyShareNotFoundException("Paillier public key")
                val R1 = Secp256k1.decompressPoint(request.r1Point)

                // Independently verify txHash matches the raw transaction fields
                val recomputedHash = withContext(Dispatchers.Default) {
                    val tx = EthSigner.buildTransaction(
                        nonce = BigInteger.valueOf(request.nonce),
                        chainId = request.chainId,
                        maxPriorityFeePerGas = BigInteger(request.maxPriorityFeePerGas, 10),
                        maxFeePerGas = BigInteger(request.maxFeePerGas, 10),
                        gasLimit = BigInteger.valueOf(request.gasLimit),
                        to = request.to,
                        value = BigInteger(request.valueWei, 10),
                        data = if (request.txData != "0x") Hex.decode(request.txData.removePrefix("0x")) else byteArrayOf()
                    )
                    tx.signingHash()
                }
                require(recomputedHash.contentEquals(request.txHash)) {
                    "Transaction hash mismatch — Vault's txHash does not match raw transaction fields"
                }

                val result = withContext(Dispatchers.Default) {
                    LindellSign.signerComputePartial(
                        x2 = x2,
                        ckey = ckey,
                        R1 = R1,
                        txHash = request.txHash,
                        paillierPk = paillierPk
                    )
                }

                val response = TransportMessage.SignResponse(
                    sessionId = request.sessionId,
                    r2Point = Secp256k1.compressPoint(result.R2),
                    cPartial = result.cPartial.toByteArray(),
                    signerR = result.r.toByteArray(),
                    approved = true
                )

                when (transportMode) {
                    TransportMode.NFC -> {
                        hceSessionManager.setSignResponse(response)
                        _state.value = SignerConfirmState.ResponseReady(TransportMode.NFC)
                        awaitResponseConsumed(response.sessionId)
                    }
                    TransportMode.QR -> {
                        val frames = transportBridge.encodeForQr(response)
                        _state.value = SignerConfirmState.DisplayingQr(frames)
                    }
                }
            } catch (e: KeyShareNotFoundException) {
                _state.value = SignerConfirmState.Failed(FlowError.KeyShareMissing(e.detail))
            } catch (e: Exception) {
                _state.value = SignerConfirmState.Failed(FlowError.CryptoFailed(
                    "Signing failed: ${e.message}"
                ))
            }
        }
    }

    private fun awaitResponseConsumed(sessionId: String) {
        viewModelScope.launch {
            hceSessionManager.responseConsumedFlow.first { it == sessionId }
            _state.value = SignerConfirmState.Done
        }
    }

    fun reject() {
        val request = signRequest ?: return
        val response = TransportMessage.SignResponse(
            sessionId = request.sessionId,
            r2Point = byteArrayOf(),
            cPartial = byteArrayOf(),
            signerR = byteArrayOf(),
            approved = false
        )
        hceSessionManager.setSignResponse(response)
        _state.value = SignerConfirmState.Done
    }

    private class KeyShareNotFoundException(val detail: String) : RuntimeException("$detail not found")
}
