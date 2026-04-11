package io.raccoonwallet.app.core.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.roundToInt

class TapEntropyCollector(
    private val targetTapCount: Int = DEFAULT_TARGET_TAP_COUNT
) {
    companion object {
        const val DEFAULT_TARGET_TAP_COUNT = 32
        private const val POSITION_SCALE = 65_535f
    }

    private val digest = MessageDigest.getInstance("SHA-256")
    private val tapBuffer = ByteBuffer.allocate(28)
    private var lastEventTimeNanos: Long? = null

    var tapCount: Int = 0
        private set

    fun addTap(
        xPx: Float,
        yPx: Float,
        widthPx: Float,
        heightPx: Float,
        eventTimeNanos: Long
    ) {
        val clampedTime = eventTimeNanos.coerceAtLeast(0L)
        val delta = lastEventTimeNanos?.let { clampedTime - it } ?: 0L
        lastEventTimeNanos = clampedTime
        tapCount += 1

        tapBuffer.clear()
        tapBuffer.putInt(tapCount)
            .putLong(clampedTime)
            .putLong(delta)
            .putInt(quantize(xPx, widthPx))
            .putInt(quantize(yPx, heightPx))
        digest.update(tapBuffer.array(), 0, 28)
    }

    fun progress(): Float = (tapCount.toFloat() / targetTapCount).coerceIn(0f, 1f)

    fun isReady(): Boolean = tapCount >= targetTapCount

    fun buildEntropy(): ByteArray? {
        if (!isReady()) return null
        val result = digest.digest()
        digest.reset()
        tapCount = 0
        lastEventTimeNanos = null
        return result
    }

    fun reset() {
        digest.reset()
        tapCount = 0
        lastEventTimeNanos = null
    }

    private fun quantize(valuePx: Float, maxPx: Float): Int {
        if (maxPx <= 0f) return 0
        val normalized = (valuePx / maxPx).coerceIn(0f, 1f)
        return (normalized * POSITION_SCALE).roundToInt()
    }
}
