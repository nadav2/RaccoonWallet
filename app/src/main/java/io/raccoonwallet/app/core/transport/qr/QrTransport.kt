package io.raccoonwallet.app.core.transport.qr

import io.raccoonwallet.app.core.transport.MessageCodec
import io.raccoonwallet.app.core.transport.TransportMessage

/**
 * QR-based transport — fallback when NFC is unavailable.
 * Data is not encrypted — rely on physical privacy during setup.
 * NFC is the recommended transport for DKG (encrypted via ECDH + AES-GCM).
 */
class QrTransport(
    private val codec: MessageCodec = MessageCodec(),
    private val encoder: QrEncoder = QrEncoder(),
    private val decoder: QrDecoder = QrDecoder()
) {
    fun encodeMessage(message: TransportMessage): List<String> {
        val payload = codec.encode(message)
        return encoder.encodeFrames(payload)
    }

    fun onFrameScanned(raw: String): ScanResult {
        return when (val result = decoder.onFrameScanned(raw)) {
            is QrDecoder.Result.Complete -> {
                try {
                    val message = codec.decode(result.payload)
                    ScanResult.MessageReady(message)
                } catch (e: Exception) {
                    ScanResult.Error("Failed to decode: ${e.message}")
                }
            }
            is QrDecoder.Result.Partial -> ScanResult.Progress(result.received, result.total)
            is QrDecoder.Result.Error -> ScanResult.Error(result.message)
        }
    }

    fun resetDecoder() { decoder.reset() }
    val scanProgress: Float get() = decoder.progress

    sealed class ScanResult {
        data class MessageReady(val message: TransportMessage) : ScanResult()
        data class Progress(val received: Int, val total: Int) : ScanResult()
        data class Error(val message: String) : ScanResult()
    }
}
