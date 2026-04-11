package io.raccoonwallet.app.core.transport.nfc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Splits large payloads into APDU-compatible chunks and reassembles them.
 *
 * Chunk format:
 * [totalChunks: 2 bytes][chunkIndex: 2 bytes][payloadType: 1 byte][reserved: 1 byte][data: up to maxChunkData bytes]
 *
 * Header = 6 bytes, so data per chunk = maxApduData - 6
 */
class ApduChunker(maxApduData: Int = 250) {

    private val headerSize = 6
    private val maxChunkData = maxApduData - headerSize

    fun chunk(payload: ByteArray, payloadType: Byte): List<ByteArray> {
        val totalChunks = (payload.size + maxChunkData - 1) / maxChunkData
        require(totalChunks <= Short.MAX_VALUE) { "Payload too large: $totalChunks chunks exceeds max" }
        val chunks = mutableListOf<ByteArray>()

        for (i in 0 until totalChunks) {
            val offset = i * maxChunkData
            val length = minOf(maxChunkData, payload.size - offset)
            val chunk = ByteBuffer.allocate(headerSize + length).apply {
                order(ByteOrder.BIG_ENDIAN)
                putShort(totalChunks.toShort())
                putShort(i.toShort())
                put(payloadType)
                put(0.toByte()) // reserved
                put(payload, offset, length)
            }
            chunks.add(chunk.array())
        }

        return chunks
    }

}
