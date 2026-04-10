package io.raccoonwallet.app.core.transport.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

/**
 * Encodes payloads into one or more QR code bitmaps.
 *
 * Single QR can hold ~2.9KB binary. For larger payloads, splits into
 * numbered frames: "FW/<totalFrames>/<frameIndex>/<base64Data>"
 *
 * The receiver scans frames in any order and reassembles.
 */
class QrEncoder(
    private val maxBytesPerFrame: Int = 1200,
    private val qrSize: Int = 600
) {
    private val writer = QRCodeWriter()
    private val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 1
    )

    /**
     * Encode a payload into QR frame strings.
     * For small payloads (<=maxBytesPerFrame), returns a single frame.
     * For large payloads, splits into multiple numbered frames.
     */
    fun encodeFrames(payload: ByteArray): List<String> {
        if (payload.size <= maxBytesPerFrame) {
            return listOf("FW/1/0/${Base64.encodeToString(payload, Base64.NO_WRAP)}")
        }

        val chunks = payload.toList().chunked(maxBytesPerFrame).map { it.toByteArray() }
        val total = chunks.size
        return chunks.mapIndexed { index, chunk ->
            "FW/$total/$index/${Base64.encodeToString(chunk, Base64.NO_WRAP)}"
        }
    }

    /**
     * Render a frame string as a QR code bitmap.
     */
    fun frameToBitmap(frame: String): Bitmap {
        val matrix = writer.encode(frame, BarcodeFormat.QR_CODE, qrSize, qrSize, hints)
        val bitmap = createBitmap(qrSize, qrSize, Bitmap.Config.RGB_565)
        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                bitmap[x, y] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return bitmap
    }

    /**
     * Convenience: encode payload directly to bitmaps.
     */
    fun encodeToBitmaps(payload: ByteArray): List<Bitmap> {
        return encodeFrames(payload).map { frameToBitmap(it) }
    }

    /**
     * Encode a plain string (e.g. an Ethereum address) as a QR bitmap.
     * Uses `ethereum:<address>` format per EIP-681 for wallet interoperability.
     */
    fun encodeAddressToBitmap(address: String, size: Int = qrSize): Bitmap {
        val content = "ethereum:$address"
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap[x, y] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return bitmap
    }
}
