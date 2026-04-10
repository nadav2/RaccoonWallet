package io.raccoonwallet.app.feature.signer.idle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.transport.TransportMessage
import io.raccoonwallet.app.core.transport.nfc.PendingSignRequest
import io.raccoonwallet.app.core.transport.qr.QrTransport
import io.raccoonwallet.app.deps
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SignerIdleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application.deps
    private val transportBridge = app.transportBridge
    private val hceSessionManager = app.hceSessionManager

    private val _incomingRequest = MutableSharedFlow<PendingSignRequest>(extraBufferCapacity = 1)
    val incomingRequest: SharedFlow<PendingSignRequest> = _incomingRequest.asSharedFlow()

    var isScanning: Boolean = false
        private set

    init {
        viewModelScope.launch {
            hceSessionManager.signRequestFlow.collect { pending ->
                _incomingRequest.tryEmit(pending)
            }
        }
    }

    fun startQrScan() {
        isScanning = true
        transportBridge.resetQrDecoder()
    }

    fun stopQrScan() {
        isScanning = false
    }

    fun onQrFrameScanned(raw: String) {
        if (!raw.startsWith("FW/")) return
        when (val result = transportBridge.decodeQrFrame(raw)) {
            is QrTransport.ScanResult.MessageReady -> {
                if (result.message is TransportMessage.SignRequest) {
                    isScanning = false
                    hceSessionManager.emitSignRequest(result.message, TransportMode.QR)
                }
            }
            else -> { /* continue scanning */ }
        }
    }
}
