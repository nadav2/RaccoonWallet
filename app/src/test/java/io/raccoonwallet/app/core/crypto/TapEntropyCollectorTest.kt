package io.raccoonwallet.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapEntropyCollectorTest {

    @Test
    fun `collector requires minimum taps before entropy is ready`() {
        val collector = TapEntropyCollector(targetTapCount = 3)

        collector.addTap(10f, 20f, 100f, 200f, eventTimeNanos = 1_000L)
        collector.addTap(30f, 40f, 100f, 200f, eventTimeNanos = 2_000L)

        assertEquals(2, collector.tapCount)
        assertTrue(collector.progress() in 0.66f..0.67f)
        assertNull(collector.buildEntropy())
    }

    @Test
    fun `collector finalizes to sha256-sized entropy once ready`() {
        val collector = TapEntropyCollector(targetTapCount = 3)

        collector.addTap(10f, 20f, 100f, 200f, eventTimeNanos = 1_000L)
        collector.addTap(30f, 40f, 100f, 200f, eventTimeNanos = 2_000L)
        collector.addTap(50f, 60f, 100f, 200f, eventTimeNanos = 3_000L)

        val entropy = collector.buildEntropy()

        assertNotNull(entropy)
        assertEquals(32, entropy?.size)
        assertEquals(0, collector.tapCount)
        assertEquals(0f, collector.progress(), 0f)
    }

    @Test
    fun `reset clears collector state`() {
        val collector = TapEntropyCollector(targetTapCount = 2)

        collector.addTap(10f, 20f, 100f, 200f, eventTimeNanos = 1_000L)
        collector.reset()

        assertEquals(0, collector.tapCount)
        assertEquals(0f, collector.progress(), 0f)
        assertNull(collector.buildEntropy())
    }
}
