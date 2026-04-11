package io.raccoonwallet.app.core.transport.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import io.raccoonwallet.app.RaccoonWalletApp
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.transport.MessageCodec
import io.raccoonwallet.app.core.transport.SessionCrypto
import io.raccoonwallet.app.core.transport.TransportMessage
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RaccoonWalletHceService : HostApduService() {

    companion object {
        private const val MAX_CHUNKS = 512

        val AID = byteArrayOf(
            0xF0.toByte(), 0x52, 0x41, 0x43, 0x4F, 0x4F, 0x4E, 0x57 // F0 "RACOONW"
        )
        val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        val SW_ERROR = byteArrayOf(0x6F, 0x00)
        val SW_NOT_READY = byteArrayOf(0x69, 0x85.toByte())

        const val INS_SESSION_INIT: Byte = 0x10
        const val INS_DKG_ROUND2: Byte = 0x22
        const val INS_SIGN_REQUEST: Byte = 0x30
        const val INS_POLL: Byte = 0x32
        const val INS_CHUNK_CONTINUE: Byte = 0xF0.toByte()
    }

    private val sessionCrypto = SessionCrypto()
    private val codec = MessageCodec()

    private val outgoingChunks = AtomicReference<List<ByteArray>?>(null)
    private val outgoingChunkIndex = AtomicInteger(0)
    private val incomingChunks = AtomicReference<MutableMap<Int, ByteArray>?>(null)
    private val incomingTotalChunks = AtomicInteger(0)
    private val incomingPayloadType = AtomicInteger(-1)

    private val maxResponseData = 248

    private val hceSessionManager: HceSessionManager
        get() = (application as RaccoonWalletApp).hceSessionManager

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (commandApdu.size < 4) return SW_ERROR

        return when (commandApdu[1]) {
            0xA4.toByte() -> {
                sessionCrypto.reset()
                outgoingChunks.set(null)
                outgoingChunkIndex.set(0)
                clearIncomingChunks()
                SW_OK
            }
            INS_SESSION_INIT -> handleSessionInit(commandApdu)
            INS_CHUNK_CONTINUE -> handleChunkContinue(commandApdu)
            INS_DKG_ROUND2 -> handleDkgRound2(commandApdu)
            INS_SIGN_REQUEST -> handleSignRequest(commandApdu)
            INS_POLL -> handlePoll()
            else -> SW_ERROR
        }
    }

    override fun onDeactivated(reason: Int) {
        sessionCrypto.reset()
        outgoingChunks.set(null)
        outgoingChunkIndex.set(0)
        clearIncomingChunks()
    }

    private fun handleSessionInit(apdu: ByteArray): ByteArray {
        val ephPubVault = extractData(apdu)
        val ephPubSigner = sessionCrypto.initiateHandshake()
        sessionCrypto.completeHandshake(ephPubVault)
        return ephPubSigner + SW_OK
    }

    private fun handleChunkContinue(apdu: ByteArray): ByteArray {
        if (!sessionCrypto.isEstablished()) return SW_ERROR
        val data = extractData(apdu)

        val chunks = outgoingChunks.get()
        if (data.isEmpty() && chunks != null) {
            val index = outgoingChunkIndex.getAndIncrement()
            if (index >= chunks.size) {
                outgoingChunks.set(null)
                return SW_ERROR
            }
            if (index == chunks.size - 1) {
                outgoingChunks.set(null)
            }
            return chunks[index] + SW_OK
        }

        return if (acceptIncomingChunk(data, expectedPayloadType = INS_SIGN_REQUEST)) SW_OK else SW_ERROR
    }

    private fun handleDkgRound2(apdu: ByteArray): ByteArray {
        if (!sessionCrypto.isEstablished()) return SW_ERROR

        val data = extractData(apdu)

        if (data.isEmpty()) {
            val bundleBytes = hceSessionManager.getDkgBundle() ?: return SW_NOT_READY
            val encrypted = sessionCrypto.encrypt(bundleBytes)

            val chunks = encrypted.toList()
                .chunked(maxResponseData)
                .map { it.toByteArray() }

            outgoingChunks.set(chunks)
            outgoingChunkIndex.set(1)

            val header = byteArrayOf(
                ((chunks.size shr 8) and 0xFF).toByte(),
                (chunks.size and 0xFF).toByte()
            )
            return header + chunks[0] + SW_OK
        } else {
            return try {
                val decrypted = sessionCrypto.decrypt(data)
                val message = codec.decode(decrypted)
                if (message is TransportMessage.DkgFinalize && message.ack) {
                    hceSessionManager.clearDkgState()
                    hceSessionManager.emitDkgAck(true)
                }
                SW_OK
            } catch (_: Exception) {
                SW_ERROR
            }
        }
    }

    private fun handleSignRequest(apdu: ByteArray): ByteArray {
        if (!sessionCrypto.isEstablished()) return SW_ERROR
        return try {
            val chunk = parseChunk(extractData(apdu))
            if (chunk.payloadType != INS_SIGN_REQUEST) return SW_ERROR
            if (!storeIncomingChunk(chunk)) return SW_ERROR
            val assembled = buildIncomingPayload() ?: return SW_OK
            val decrypted = sessionCrypto.decrypt(assembled)
            val message = codec.decode(decrypted)
            if (message is TransportMessage.SignRequest) {
                hceSessionManager.emitSignRequest(message, TransportMode.NFC)
            }
            clearIncomingChunks()
            SW_OK
        } catch (_: Exception) {
            clearIncomingChunks()
            SW_ERROR
        }
    }

    private fun handlePoll(): ByteArray {
        if (!sessionCrypto.isEstablished()) return SW_ERROR
        val response = hceSessionManager.consumeSignResponse() ?: return SW_NOT_READY
        val encoded = codec.encode(response)
        val encrypted = sessionCrypto.encrypt(encoded)

        val chunks = encrypted.toList()
            .chunked(maxResponseData)
            .map { it.toByteArray() }

        if (chunks.size > 1) {
            outgoingChunks.set(chunks)
            outgoingChunkIndex.set(1)
        }

        val header = byteArrayOf(
            ((chunks.size shr 8) and 0xFF).toByte(),
            (chunks.size and 0xFF).toByte()
        )
        return header + chunks[0] + SW_OK
    }

    private fun extractData(apdu: ByteArray): ByteArray {
        if (apdu.size <= 5) return byteArrayOf()
        val lc = apdu[4].toInt() and 0xFF
        return apdu.copyOfRange(5, minOf(5 + lc, apdu.size))
    }

    private class ParsedChunk(
        val totalChunks: Int,
        val chunkIndex: Int,
        val payloadType: Byte,
        val payload: ByteArray
    )

    private fun parseChunk(data: ByteArray): ParsedChunk {
        require(data.size >= 6) { "Chunk too short" }
        val totalChunks = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val chunkIndex = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val payloadType = data[4]
        val payload = data.copyOfRange(6, data.size)
        require(totalChunks in 1..MAX_CHUNKS) { "Invalid chunk count: $totalChunks" }
        require(chunkIndex in 0 until totalChunks) { "Chunk index out of range" }
        return ParsedChunk(totalChunks, chunkIndex, payloadType, payload)
    }

    private fun acceptIncomingChunk(data: ByteArray, expectedPayloadType: Byte): Boolean {
        return try {
            val chunk = parseChunk(data)
            if (chunk.payloadType != expectedPayloadType) return false
            storeIncomingChunk(chunk)
        } catch (_: Exception) {
            false
        }
    }

    private fun storeIncomingChunk(chunk: ParsedChunk): Boolean {
        val currentTotal = incomingTotalChunks.get()
        val currentType = incomingPayloadType.get()
        if (currentTotal != 0 && (currentTotal != chunk.totalChunks || currentType != chunk.payloadType.toInt())) {
            clearIncomingChunks()
        }
        if (incomingChunks.get() == null) {
            incomingChunks.set(mutableMapOf())
            incomingTotalChunks.set(chunk.totalChunks)
            incomingPayloadType.set(chunk.payloadType.toInt())
        }
        incomingChunks.get()?.set(chunk.chunkIndex, chunk.payload)
        return true
    }

    private fun buildIncomingPayload(): ByteArray? {
        val chunks = incomingChunks.get() ?: return null
        val total = incomingTotalChunks.get()
        if (chunks.size != total || total <= 0) return null
        val out = ByteArrayOutputStream()
        for (i in 0 until total) {
            val piece = chunks[i] ?: return null
            out.write(piece)
        }
        return out.toByteArray()
    }

    private fun clearIncomingChunks() {
        incomingChunks.set(null)
        incomingTotalChunks.set(0)
        incomingPayloadType.set(-1)
    }
}
