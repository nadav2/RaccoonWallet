package io.raccoonwallet.app.core.transport.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Bundle
import io.raccoonwallet.app.core.transport.MessageCodec
import io.raccoonwallet.app.core.transport.SessionCrypto
import io.raccoonwallet.app.core.transport.TransportMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Vault-side NFC reader. Sends APDU commands to Signer's HCE service.
 */
class NfcReaderTransport(
    private val chunker: ApduChunker,
    private val codec: MessageCodec
) {
    private var isoDep: IsoDep? = null
    private val sessionCrypto = SessionCrypto()

    /**
     * Enable reader mode on the activity and wait for a tag to be discovered.
     */
    suspend fun waitForTag(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        val callback = NfcAdapter.ReaderCallback { tag ->
            val success = try {
                connectAndHandshake(tag)
            } catch (_: Exception) {
                false
            }
            if (cont.isActive) cont.resume(success)
        }

        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 500)
        }

        adapter.enableReaderMode(
            activity,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            options
        )

        cont.invokeOnCancellation {
            adapter.disableReaderMode(activity)
        }
    }

    /**
     * Send a transport message and receive a response, handling chunking and encryption.
     */
    fun sendAndReceive(insCode: Byte, message: TransportMessage): TransportMessage {
        val iso = isoDep ?: throw IllegalStateException("Not connected")
        val encoded = codec.encode(message)
        // MED-3: Require established session — never send/receive unencrypted
        require(sessionCrypto.isEstablished()) { "Cannot send without established session" }
        val encrypted = sessionCrypto.encrypt(encoded)

        // Chunk if necessary
        val chunks = chunker.chunk(encrypted, insCode)

        var lastResponse: ByteArray = byteArrayOf()
        for ((index, chunk) in chunks.withIndex()) {
            val ins = if (index < chunks.size - 1) RaccoonWalletHceService.INS_CHUNK_CONTINUE else insCode
            val apdu = buildApdu(ins, chunk)
            lastResponse = iso.transceive(apdu)
            // Check intermediate chunk responses — don't continue sending on error
            if (index < chunks.size - 1 && !isSwOk(lastResponse)) {
                throw RuntimeException("APDU error on chunk $index: ${lastResponse.joinToString { "%02X".format(it) }}")
            }
        }

        // Parse response
        if (lastResponse.size <= 2) {
            if (isSwOk(lastResponse)) {
                return TransportMessage.Error(0, "ACK only, no data")
            }
            throw RuntimeException("APDU error: ${lastResponse.joinToString { "%02X".format(it) }}")
        }

        val responseData = lastResponse.copyOfRange(0, lastResponse.size - 2)
        val decrypted = sessionCrypto.decrypt(responseData)
        return codec.decode(decrypted)
    }

    /**
     * Send a transport message expecting only SW_OK (no response data).
     * Used for delivering SignRequest on Tap 1.
     */
    fun sendOnly(insCode: Byte, message: TransportMessage) {
        val iso = isoDep ?: throw IllegalStateException("Not connected")
        require(sessionCrypto.isEstablished()) { "Cannot send without established session" }
        val encoded = codec.encode(message)
        val encrypted = sessionCrypto.encrypt(encoded)
        val chunks = chunker.chunk(encrypted, insCode)

        for ((index, chunk) in chunks.withIndex()) {
            val ins = if (index < chunks.size - 1) RaccoonWalletHceService.INS_CHUNK_CONTINUE else insCode
            val apdu = buildApdu(ins, chunk)
            val response = iso.transceive(apdu)
            if (!isSwOk(response)) {
                throw RuntimeException("APDU error on sendOnly: ${response.joinToString { "%02X".format(it) }}")
            }
        }
    }

    /**
     * Poll for a SignResponse from the Signer. Sends INS_POLL with empty data.
     * Returns the decoded response, or null if the Signer is not ready (SW_NOT_READY).
     * Handles chunked responses (same pattern as fetchDkgBundle).
     */
    fun pollForResponse(): TransportMessage? {
        val iso = isoDep ?: throw IllegalStateException("Not connected")
        require(sessionCrypto.isEstablished()) { "Session not established" }

        val firstResponse = iso.transceive(buildApdu(RaccoonWalletHceService.INS_POLL, byteArrayOf()))

        // SW_NOT_READY → signer hasn't approved yet
        if (firstResponse.size == 2 && firstResponse[0] == 0x69.toByte() && firstResponse[1] == 0x85.toByte()) {
            return null
        }
        if (!isSwOk(firstResponse) || firstResponse.size <= 2) {
            throw RuntimeException("Poll APDU error: ${firstResponse.joinToString { "%02X".format(it) }}")
        }

        val firstPayload = firstResponse.copyOfRange(0, firstResponse.size - 2)
        require(firstPayload.size >= 2) { "Poll response too short" }

        // HCE always prepends a 2-byte chunk count header
        val totalChunks = ((firstPayload[0].toInt() and 0xFF) shl 8) or
            (firstPayload[1].toInt() and 0xFF)
        val allChunks = mutableListOf(firstPayload.copyOfRange(2, firstPayload.size))

        // Fetch remaining chunks if multi-part
        for (i in 1 until totalChunks) {
            val resp = iso.transceive(buildApdu(RaccoonWalletHceService.INS_CHUNK_CONTINUE, byteArrayOf()))
            if (!isSwOk(resp) || resp.size <= 2) {
                throw RuntimeException("Chunk $i fetch failed during poll")
            }
            allChunks.add(resp.copyOfRange(0, resp.size - 2))
        }

        val assembled = ByteArray(allChunks.sumOf { it.size })
        var offset = 0
        for (chunk in allChunks) {
            chunk.copyInto(assembled, offset)
            offset += chunk.size
        }
        val decrypted = sessionCrypto.decrypt(assembled)
        return codec.decode(decrypted)
    }

    /**
     * Fetch the DKG bundle from Signer's HCE service via chunked pull.
     * Must be called after waitForTag() succeeds (session established).
     */
    fun fetchDkgBundle(onProgress: (Float) -> Unit = {}): TransportMessage {
        val iso = isoDep ?: throw IllegalStateException("Not connected")
        require(sessionCrypto.isEstablished()) { "Session not established" }

        // Request bundle with empty INS_DKG_ROUND2
        val firstResponse = iso.transceive(buildApdu(RaccoonWalletHceService.INS_DKG_ROUND2, byteArrayOf()))
        if (!isSwOk(firstResponse) || firstResponse.size <= 2) {
            throw RuntimeException("Signer not ready or NFC error")
        }

        val firstPayload = firstResponse.copyOfRange(0, firstResponse.size - 2)
        require(firstPayload.size >= 2) { "Response too short" }

        // First 2 bytes = total chunk count, rest = chunk 0 data
        val totalChunks = ((firstPayload[0].toInt() and 0xFF) shl 8) or
            (firstPayload[1].toInt() and 0xFF)
        val allChunks = mutableListOf(firstPayload.copyOfRange(2, firstPayload.size))
        onProgress(1f / totalChunks)

        // Fetch remaining chunks
        for (i in 1 until totalChunks) {
            val resp = iso.transceive(buildApdu(RaccoonWalletHceService.INS_CHUNK_CONTINUE, byteArrayOf()))
            if (!isSwOk(resp) || resp.size <= 2) {
                throw RuntimeException("Chunk $i fetch failed")
            }
            allChunks.add(resp.copyOfRange(0, resp.size - 2))
            onProgress((i + 1).toFloat() / totalChunks)
        }

        // Reassemble + decrypt + decode
        val assembled = ByteArray(allChunks.sumOf { it.size })
        var offset = 0
        for (chunk in allChunks) {
            chunk.copyInto(assembled, offset)
            offset += chunk.size
        }

        val decrypted = sessionCrypto.decrypt(assembled)
        return codec.decode(decrypted)
    }

    /**
     * Send ACK to Signer after successfully receiving the DKG bundle.
     */
    fun sendDkgAck() {
        val iso = isoDep ?: throw IllegalStateException("Not connected")
        require(sessionCrypto.isEstablished()) { "Session not established" }

        val ack = TransportMessage.DkgFinalize(ack = true)
        val encrypted = sessionCrypto.encrypt(codec.encode(ack))
        // Size checked in buildApdu (max 255 bytes)
        val resp = iso.transceive(buildApdu(RaccoonWalletHceService.INS_DKG_ROUND2, encrypted))
        if (!isSwOk(resp)) {
            throw RuntimeException("ACK send failed")
        }
    }

    fun disconnect() {
        try { isoDep?.close() } catch (_: Exception) {}
        isoDep = null
        sessionCrypto.reset()
    }

    private fun connectAndHandshake(tag: android.nfc.Tag): Boolean {
        val iso = IsoDep.get(tag) ?: return false
        iso.connect()
        iso.timeout = 5000
        isoDep = iso

        // SELECT AID
        val selectResp = iso.transceive(buildSelectAidApdu(RaccoonWalletHceService.AID))
        if (!isSwOk(selectResp)) return false

        // ECDH handshake
        val ephPub = sessionCrypto.initiateHandshake()
        val initResp = iso.transceive(buildApdu(RaccoonWalletHceService.INS_SESSION_INIT, ephPub))
        if (initResp.size <= 2 || !isSwOk(initResp)) return false

        val signerPub = initResp.copyOfRange(0, initResp.size - 2)
        sessionCrypto.completeHandshake(signerPub)
        return true
    }

    private fun buildSelectAidApdu(aid: ByteArray): ByteArray {
        return byteArrayOf(
            0x00, // CLA
            0xA4.toByte(), // INS: SELECT
            0x04, // P1: Select by name
            0x00, // P2
            aid.size.toByte()
        ) + aid
    }

    private fun buildApdu(ins: Byte, data: ByteArray): ByteArray {
        // MED-6: Reject payloads that don't fit in a single-byte Lc field
        require(data.size <= 255) { "APDU data too large: ${data.size} bytes (max 255)" }
        return byteArrayOf(
            0x00, // CLA
            ins,
            0x00, // P1
            0x00, // P2
            data.size.toByte()
        ) + data
    }

    private fun isSwOk(response: ByteArray): Boolean {
        return response.size >= 2 &&
            response[response.size - 2] == 0x90.toByte() &&
            response[response.size - 1] == 0x00.toByte()
    }
}
