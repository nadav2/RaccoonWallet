package io.raccoonwallet.app.core.transport.qr

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class QrCodecTest {

    private lateinit var decoder: QrDecoder

    @Before
    fun setUp() {
        // Inject java.util.Base64 for JVM tests (instead of android.util.Base64)
        decoder = QrDecoder(
            base64Decode = { Base64.getDecoder().decode(it) }
        )
    }

    @Test
    fun `single frame decode`() {
        val payload = "Hello World".toByteArray()
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(payload)
        val frame = "FW/1/0/$encoded"

        val result = decoder.onFrameScanned(frame)

        assertTrue(result is QrDecoder.Result.Complete)
        assertArrayEquals(payload, (result as QrDecoder.Result.Complete).payload)
    }

    @Test
    fun `multi frame decode in order`() {
        val part1 = "Part1".toByteArray()
        val part2 = "Part2".toByteArray()
        val enc1 = Base64.getEncoder().withoutPadding().encodeToString(part1)
        val enc2 = Base64.getEncoder().withoutPadding().encodeToString(part2)

        val r1 = decoder.onFrameScanned("FW/2/0/$enc1")
        assertTrue(r1 is QrDecoder.Result.Partial)
        assertEquals(1, (r1 as QrDecoder.Result.Partial).received)
        assertEquals(2, r1.total)

        val r2 = decoder.onFrameScanned("FW/2/1/$enc2")
        assertTrue(r2 is QrDecoder.Result.Complete)
        assertArrayEquals(part1 + part2, (r2 as QrDecoder.Result.Complete).payload)
    }

    @Test
    fun `multi frame decode out of order`() {
        val part1 = "First".toByteArray()
        val part2 = "Second".toByteArray()
        val part3 = "Third".toByteArray()
        val enc1 = Base64.getEncoder().withoutPadding().encodeToString(part1)
        val enc2 = Base64.getEncoder().withoutPadding().encodeToString(part2)
        val enc3 = Base64.getEncoder().withoutPadding().encodeToString(part3)

        // Scan in reverse order
        val r3 = decoder.onFrameScanned("FW/3/2/$enc3")
        assertTrue(r3 is QrDecoder.Result.Partial)

        val r1 = decoder.onFrameScanned("FW/3/0/$enc1")
        assertTrue(r1 is QrDecoder.Result.Partial)

        val r2 = decoder.onFrameScanned("FW/3/1/$enc2")
        assertTrue(r2 is QrDecoder.Result.Complete)
        assertArrayEquals(part1 + part2 + part3, (r2 as QrDecoder.Result.Complete).payload)
    }

    @Test
    fun `duplicate frame is idempotent`() {
        val part1 = "A".toByteArray()
        val part2 = "B".toByteArray()
        val enc1 = Base64.getEncoder().withoutPadding().encodeToString(part1)
        val enc2 = Base64.getEncoder().withoutPadding().encodeToString(part2)

        decoder.onFrameScanned("FW/2/0/$enc1")
        val dup = decoder.onFrameScanned("FW/2/0/$enc1")
        assertTrue(dup is QrDecoder.Result.Partial)
        assertEquals(1, (dup as QrDecoder.Result.Partial).received)

        val r2 = decoder.onFrameScanned("FW/2/1/$enc2")
        assertTrue(r2 is QrDecoder.Result.Complete)
    }

    @Test
    fun `reset clears state`() {
        val enc = Base64.getEncoder().withoutPadding().encodeToString("data".toByteArray())
        decoder.onFrameScanned("FW/3/0/$enc")
        assertEquals(1f / 3f, decoder.progress, 0.01f)

        decoder.reset()
        assertEquals(0f, decoder.progress, 0.001f)
    }

    @Test
    fun `non-raccoonwallet frame returns error`() {
        val result = decoder.onFrameScanned("INVALID/data")
        assertTrue(result is QrDecoder.Result.Error)
    }

    @Test
    fun `malformed frame returns error`() {
        val result = decoder.onFrameScanned("FW/abc/0/data")
        assertTrue(result is QrDecoder.Result.Error)
    }

    @Test
    fun `invalid base64 returns error`() {
        val result = decoder.onFrameScanned("FW/1/0/!!!invalid!!!")
        assertTrue(result is QrDecoder.Result.Error)
    }

    @Test
    fun `frame index out of range returns error`() {
        val enc = Base64.getEncoder().withoutPadding().encodeToString("data".toByteArray())
        val result = decoder.onFrameScanned("FW/2/5/$enc")
        assertTrue(result is QrDecoder.Result.Error)
    }

    @Test
    fun `new transmission resets previous state`() {
        val enc1 = Base64.getEncoder().withoutPadding().encodeToString("old".toByteArray())
        decoder.onFrameScanned("FW/3/0/$enc1") // Part of a 3-frame transmission

        // New transmission with different total
        val newEnc = Base64.getEncoder().withoutPadding().encodeToString("new".toByteArray())
        val result = decoder.onFrameScanned("FW/1/0/$newEnc")
        assertTrue(result is QrDecoder.Result.Complete)
        assertArrayEquals("new".toByteArray(), (result as QrDecoder.Result.Complete).payload)
    }

    @Test
    fun `progress tracking`() {
        val enc = Base64.getEncoder().withoutPadding().encodeToString("x".toByteArray())

        assertEquals(0f, decoder.progress, 0.001f)

        decoder.onFrameScanned("FW/4/0/$enc")
        assertEquals(0.25f, decoder.progress, 0.001f)

        decoder.onFrameScanned("FW/4/1/$enc")
        assertEquals(0.5f, decoder.progress, 0.001f)

        decoder.onFrameScanned("FW/4/2/$enc")
        assertEquals(0.75f, decoder.progress, 0.001f)

        // After complete, resets
        decoder.onFrameScanned("FW/4/3/$enc")
        assertEquals(0f, decoder.progress, 0.001f)
    }
}
