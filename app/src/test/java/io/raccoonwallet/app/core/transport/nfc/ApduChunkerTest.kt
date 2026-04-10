package io.raccoonwallet.app.core.transport.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApduChunkerTest {

    private val chunker = ApduChunker(maxApduData = 250)
    private val headerSize = 6
    private val maxChunkData = 250 - headerSize // 244

    @Test
    fun `single chunk for small payload`() {
        val payload = ByteArray(100) { it.toByte() }
        val chunks = chunker.chunk(payload, 0x30)

        assertEquals(1, chunks.size)
        val chunk = chunks[0]
        assertEquals(headerSize + 100, chunk.size)

        // Verify header
        val header = parseHeader(chunk)
        assertEquals(1, header.totalChunks)
        assertEquals(0, header.chunkIndex)
        assertEquals(0x30.toByte(), header.payloadType)

        // Verify data
        assertArrayEquals(payload, chunk.copyOfRange(headerSize, chunk.size))
    }

    @Test
    fun `multiple chunks for large payload`() {
        val payload = ByteArray(500) { it.toByte() }
        val chunks = chunker.chunk(payload, 0x30)

        // 500 / 244 = 2.05 → 3 chunks
        assertEquals(3, chunks.size)

        // Verify all headers have correct totalChunks
        for ((i, chunk) in chunks.withIndex()) {
            val header = parseHeader(chunk)
            assertEquals(3, header.totalChunks)
            assertEquals(i, header.chunkIndex)
            assertEquals(0x30.toByte(), header.payloadType)
        }

        // Verify reassembly
        val reassembled = ByteArray(chunks.sumOf { it.size - headerSize })
        var offset = 0
        for (chunk in chunks) {
            val data = chunk.copyOfRange(headerSize, chunk.size)
            data.copyInto(reassembled, offset)
            offset += data.size
        }
        assertArrayEquals(payload, reassembled)
    }

    @Test
    fun `exact boundary payload fits in single chunk`() {
        val payload = ByteArray(maxChunkData) { 0xAB.toByte() }
        val chunks = chunker.chunk(payload, 0x10)

        assertEquals(1, chunks.size)
        assertEquals(headerSize + maxChunkData, chunks[0].size)
    }

    @Test
    fun `one byte over boundary creates two chunks`() {
        val payload = ByteArray(maxChunkData + 1) { 0xCD.toByte() }
        val chunks = chunker.chunk(payload, 0x10)

        assertEquals(2, chunks.size)
        // First chunk should be full
        assertEquals(headerSize + maxChunkData, chunks[0].size)
        // Second chunk has 1 byte
        assertEquals(headerSize + 1, chunks[1].size)
    }

    @Test
    fun `empty payload produces no chunks`() {
        val payload = ByteArray(0)
        val chunks = chunker.chunk(payload, 0x22)

        // (0 + 244 - 1) / 244 = 0 — no data to chunk
        assertEquals(0, chunks.size)
    }

    @Test
    fun `payload type byte preserved across chunks`() {
        val payload = ByteArray(600)
        val chunks = chunker.chunk(payload, 0xF0.toByte())

        for (chunk in chunks) {
            val header = parseHeader(chunk)
            assertEquals(0xF0.toByte(), header.payloadType)
        }
    }

    private data class ChunkHeader(
        val totalChunks: Int,
        val chunkIndex: Int,
        val payloadType: Byte
    )

    private fun parseHeader(chunk: ByteArray): ChunkHeader {
        val buf = ByteBuffer.wrap(chunk).order(ByteOrder.BIG_ENDIAN)
        val totalChunks = buf.short.toInt() and 0xFFFF
        val chunkIndex = buf.short.toInt() and 0xFFFF
        val payloadType = buf.get()
        buf.get() // reserved
        return ChunkHeader(totalChunks, chunkIndex, payloadType)
    }
}
