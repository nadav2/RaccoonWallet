package io.raccoonwallet.app.core.transport.qr

import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Reassembles multi-frame QR payloads.
 *
 * Frames have format: "FW/<totalFrames>/<frameIndex>/<base64Data>"
 * Frames can arrive in any order. Signals when all frames are received.
 */
class QrDecoder(
    private val base64Decode: (String) -> ByteArray = { Base64.decode(it, Base64.NO_WRAP) }
) {

    // All state access synchronized for thread safety (camera analyzer callbacks)
    private val lock = Any()
    private var totalFrames: Int = -1
    private val receivedFrames = mutableMapOf<Int, ByteArray>()

    sealed class Result {
        data class Partial(val received: Int, val total: Int) : Result()
        class Complete(val payload: ByteArray) : Result()
        data class Error(val message: String) : Result()
    }

    fun onFrameScanned(raw: String): Result = synchronized(lock) {
        if (!raw.startsWith("FW/")) {
            return Result.Error("Not a Raccoon Wallet QR frame")
        }

        val parts = raw.split("/", limit = 4)
        if (parts.size != 4) {
            return Result.Error("Malformed frame: expected FW/total/index/data")
        }

        val total = parts[1].toIntOrNull() ?: return Result.Error("Invalid total: ${parts[1]}")
        val index = parts[2].toIntOrNull() ?: return Result.Error("Invalid index: ${parts[2]}")
        val data = try {
            base64Decode(parts[3])
        } catch (e: Exception) {
            return Result.Error("Invalid base64 data: ${e.message}")
        }

        if (totalFrames == -1) {
            totalFrames = total
        } else if (totalFrames != total) {
            // New transmission started — reset
            reset()
            totalFrames = total
        }

        if (index !in 0 until total) {
            return Result.Error("Frame index $index out of range [0, $total)")
        }

        receivedFrames[index] = data

        if (receivedFrames.size == totalFrames) {
            val output = ByteArrayOutputStream()
            for (i in 0..<total) {
                val frame = receivedFrames[i] ?: return Result.Error("Missing frame $i")
                output.write(frame)
            }
            val payload = output.toByteArray()
            reset()
            return Result.Complete(payload)
        }

        return Result.Partial(receivedFrames.size, totalFrames)
    }

    fun reset() = synchronized(lock) {
        totalFrames = -1
        receivedFrames.clear()
    }

    val progress: Float
        get() = synchronized(lock) {
            if (totalFrames <= 0) 0f else receivedFrames.size.toFloat() / totalFrames
        }
}
