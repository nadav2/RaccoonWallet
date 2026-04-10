package io.raccoonwallet.app.core.transport

import io.raccoonwallet.app.core.model.TxDisplayData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {

    private val codec = MessageCodec()

    @Test
    fun `roundtrip DkgFinalize`() {
        val original = TransportMessage.DkgFinalize(ack = true)
        val encoded = codec.encode(original)
        val decoded = codec.decode(encoded)

        assertTrue(decoded is TransportMessage.DkgFinalize)
        assertEquals(true, (decoded as TransportMessage.DkgFinalize).ack)
    }

    @Test
    fun `roundtrip DkgFinalize false`() {
        val original = TransportMessage.DkgFinalize(ack = false)
        val decoded = codec.decode(codec.encode(original))

        assertTrue(decoded is TransportMessage.DkgFinalize)
        assertEquals(false, (decoded as TransportMessage.DkgFinalize).ack)
    }

    @Test
    fun `roundtrip DkgRound2`() {
        val q1Points = listOf(
            byteArrayOf(1, 2, 3),
            byteArrayOf(4, 5, 6),
            byteArrayOf(7, 8, 9),
            byteArrayOf(10, 11, 12)
        )
        val ckeys = listOf(
            byteArrayOf(0x0A, 0x0B),
            byteArrayOf(0x0C, 0x0D)
        )
        val original = TransportMessage.DkgRound2(q1Points = q1Points, ckeys = ckeys)
        val decoded = codec.decode(codec.encode(original))

        assertTrue(decoded is TransportMessage.DkgRound2)
        val round2 = decoded as TransportMessage.DkgRound2
        assertEquals(4, round2.q1Points.size)
        assertEquals(2, round2.ckeys.size)
        assertArrayEquals(byteArrayOf(1, 2, 3), round2.q1Points[0])
        assertArrayEquals(byteArrayOf(10, 11, 12), round2.q1Points[3])
        assertArrayEquals(byteArrayOf(0x0C, 0x0D), round2.ckeys[1])
    }

    @Test
    fun `roundtrip SignRequest`() {
        val displayData = TxDisplayData(
            chainName = "Ethereum",
            fromAddress = "0x1234567890abcdef1234567890abcdef12345678",
            toAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            value = "1.5 ETH",
            data = "0x",
            estimatedFee = "0.002 ETH"
        )
        val original = TransportMessage.SignRequest(
            sessionId = "test-session-123",
            accountIndex = 2,
            chainId = 137L,
            txHash = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()),
            r1Point = byteArrayOf(0x02, 0x33, 0x44),
            displayData = displayData
        )

        val decoded = codec.decode(codec.encode(original))

        assertTrue(decoded is TransportMessage.SignRequest)
        val req = decoded as TransportMessage.SignRequest
        assertEquals("test-session-123", req.sessionId)
        assertEquals(2, req.accountIndex)
        assertEquals(137L, req.chainId)
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), req.txHash)
        assertArrayEquals(byteArrayOf(0x02, 0x33, 0x44), req.r1Point)
        assertEquals("Ethereum", req.displayData.chainName)
        assertEquals("1.5 ETH", req.displayData.value)
        assertEquals("0.002 ETH", req.displayData.estimatedFee)
    }

    @Test
    fun `roundtrip SignResponse approved`() {
        val original = TransportMessage.SignResponse(
            sessionId = "session-456",
            r2Point = byteArrayOf(0x03, 0x55, 0x66),
            cPartial = byteArrayOf(0x77, 0x88.toByte()),
            signerR = byteArrayOf(0x99.toByte(), 0xAA.toByte()),
            approved = true
        )

        val decoded = codec.decode(codec.encode(original))

        assertTrue(decoded is TransportMessage.SignResponse)
        val resp = decoded as TransportMessage.SignResponse
        assertEquals("session-456", resp.sessionId)
        assertTrue(resp.approved)
        assertArrayEquals(byteArrayOf(0x03, 0x55, 0x66), resp.r2Point)
        assertArrayEquals(byteArrayOf(0x77, 0x88.toByte()), resp.cPartial)
    }

    @Test
    fun `roundtrip SignResponse rejected`() {
        val original = TransportMessage.SignResponse(
            sessionId = "session-789",
            r2Point = byteArrayOf(),
            cPartial = byteArrayOf(),
            signerR = byteArrayOf(),
            approved = false
        )

        val decoded = codec.decode(codec.encode(original))

        assertTrue(decoded is TransportMessage.SignResponse)
        assertEquals(false, (decoded as TransportMessage.SignResponse).approved)
    }

    @Test
    fun `roundtrip Error message`() {
        val original = TransportMessage.Error(code = 42, message = "Something went wrong")
        val decoded = codec.decode(codec.encode(original))

        assertTrue(decoded is TransportMessage.Error)
        val err = decoded as TransportMessage.Error
        assertEquals(42, err.code)
        assertEquals("Something went wrong", err.message)
    }

    @Test
    fun `large DkgRound2 bundle`() {
        // Simulate a realistic DKG bundle with 8 accounts
        val q1Points = mutableListOf<ByteArray>()
        // 4 Paillier fields + 8 compressed public keys
        repeat(12) { q1Points.add(ByteArray(256) { it.toByte() }) }
        val ckeys = (0 until 8).map { ByteArray(32) { (it + 1).toByte() } }

        val original = TransportMessage.DkgRound2(q1Points = q1Points, ckeys = ckeys)
        val encoded = codec.encode(original)
        val decoded = codec.decode(encoded) as TransportMessage.DkgRound2

        assertEquals(12, decoded.q1Points.size)
        assertEquals(8, decoded.ckeys.size)
        assertArrayEquals(q1Points[0], decoded.q1Points[0])
        assertArrayEquals(ckeys[7], decoded.ckeys[7])
    }
}
