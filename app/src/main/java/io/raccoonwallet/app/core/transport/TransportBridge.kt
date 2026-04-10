package io.raccoonwallet.app.core.transport

import android.app.Activity
import io.raccoonwallet.app.core.transport.nfc.NfcReaderTransport
import io.raccoonwallet.app.core.transport.qr.QrTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Shared transport abstraction for both DKG and signing flows.
 * Wraps NFC and QR into a unified interface usable by any ViewModel.
 */
class TransportBridge(
    val nfcReader: NfcReaderTransport,
    val qrTransport: QrTransport
) {

    // ── NFC Operations ──

    /**
     * Wait for NFC tag, establish ECDH session, send a message, then disconnect.
     * Used for Tap 1 (delivering SignRequest to Signer).
     */
    suspend fun nfcSendAndDisconnect(
        activity: Activity,
        insCode: Byte,
        message: TransportMessage
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val connected = nfcReader.waitForTag(activity)
            if (!connected) return@withContext false
            nfcReader.sendOnly(insCode, message)
            nfcReader.disconnect()
            true
        } catch (e: Exception) {
            nfcReader.disconnect()
            throw e
        }
    }

    /**
     * Wait for NFC tag, establish ECDH session, poll for a response, disconnect.
     * Used for Tap 2 (retrieving SignResponse from Signer).
     * Returns the response, or null if the Signer rejected / timed out.
     */
    suspend fun nfcPollAndDisconnect(
        activity: Activity,
        timeoutMs: Long = 30_000
    ): TransportMessage? = withContext(Dispatchers.IO) {
        try {
            val connected = nfcReader.waitForTag(activity)
            if (!connected) return@withContext null
            val deadline = System.currentTimeMillis() + timeoutMs
            var response: TransportMessage? = null
            while (System.currentTimeMillis() < deadline) {
                response = nfcReader.pollForResponse()
                if (response != null) break
                delay(500)
            }
            nfcReader.disconnect()
            response
        } catch (e: Exception) {
            nfcReader.disconnect()
            throw e
        }
    }

    // ── QR Operations ──

    fun encodeForQr(message: TransportMessage): List<String> {
        return qrTransport.encodeMessage(message)
    }

    fun decodeQrFrame(raw: String): QrTransport.ScanResult {
        return qrTransport.onFrameScanned(raw)
    }

    fun resetQrDecoder() {
        qrTransport.resetDecoder()
    }

    /** Disconnect NFC and reset QR decoder — single call for retry/cancel paths. */
    fun cleanup() {
        nfcReader.disconnect()
        resetQrDecoder()
    }
}
