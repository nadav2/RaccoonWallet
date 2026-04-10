package io.raccoonwallet.app.core.transport.nfc

import android.os.SystemClock
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.transport.TransportMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class PendingSignRequest(
    val request: TransportMessage.SignRequest,
    val transportMode: TransportMode
)

/**
 * Manages shared transient state for the HCE (Host Card Emulation) service.
 *
 * Previously this state lived on RaccoonWalletHceService.Companion, which meant it
 * survived service destruction but was invisible to the rest of the app. By
 * extracting it into a dedicated manager owned by RaccoonWalletApp, we get:
 *
 * - Explicit lifecycle: clearAll() / clearDkgState() / clearSignState()
 * - Session expiry: stale data auto-rejected after 5 minutes
 * - Single wipe point for retry/cancel paths
 * - No more static mutable state on a Service companion
 */
class HceSessionManager {

    companion object {
        const val SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }

    // ── Sign request/response ──

    private val _signRequestFlow = MutableSharedFlow<PendingSignRequest>(
        replay = 1, extraBufferCapacity = 1
    )
    val signRequestFlow: SharedFlow<PendingSignRequest> = _signRequestFlow.asSharedFlow()

    private val pendingSignResponse = AtomicReference<TransportMessage.SignResponse?>(null)

    private val _responseConsumedFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits the sessionId when the Vault pulls the sign response via NFC. */
    val responseConsumedFlow: SharedFlow<String> = _responseConsumedFlow.asSharedFlow()

    fun setSignResponse(response: TransportMessage.SignResponse) {
        pendingSignResponse.set(response)
        touch()
    }

    fun consumeSignResponse(): TransportMessage.SignResponse? {
        if (isSessionExpired()) {
            clearSignState()
            return null
        }
        val response = pendingSignResponse.getAndSet(null)
        if (response != null) {
            _responseConsumedFlow.tryEmit(response.sessionId)
        }
        return response
    }

    fun emitSignRequest(request: TransportMessage.SignRequest, transportMode: TransportMode) {
        _signRequestFlow.tryEmit(PendingSignRequest(request, transportMode))
        touch()
    }

    // ── DKG bundle ──

    private val pendingDkgBundle = AtomicReference<ByteArray?>(null)

    private val _dkgAckFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val dkgAckFlow: SharedFlow<Boolean> = _dkgAckFlow.asSharedFlow()

    fun setDkgBundle(encodedBundle: ByteArray) {
        pendingDkgBundle.set(encodedBundle)
        touch()
    }

    fun getDkgBundle(): ByteArray? {
        if (isSessionExpired()) {
            clearDkgState()
            return null
        }
        return pendingDkgBundle.get()
    }

    fun emitDkgAck(ack: Boolean) {
        _dkgAckFlow.tryEmit(ack)
    }

    // ── Session tracking ──

    private val lastActivityTime = AtomicLong(0L)

    fun touch() {
        lastActivityTime.set(SystemClock.elapsedRealtime())
    }

    fun isSessionExpired(): Boolean {
        val last = lastActivityTime.get()
        return last > 0L && SystemClock.elapsedRealtime() - last > SESSION_TIMEOUT_MS
    }

    // ── Cleanup ──

    fun clearDkgState() {
        pendingDkgBundle.set(null)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearSignState() {
        pendingSignResponse.set(null)
        _signRequestFlow.resetReplayCache()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearAll() {
        pendingSignResponse.set(null)
        pendingDkgBundle.set(null)
        lastActivityTime.set(0L)
        _signRequestFlow.resetReplayCache()
    }
}
