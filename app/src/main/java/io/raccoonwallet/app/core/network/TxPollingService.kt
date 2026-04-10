package io.raccoonwallet.app.core.network

import io.raccoonwallet.app.core.model.TxStatus
import io.raccoonwallet.app.core.storage.PublicStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class TxPollingService(
    private val publicStore: PublicStore,
    private val chainManager: ChainManager
) {
    private var pollingJob: Job? = null

    fun startPolling(scope: CoroutineScope, onUpdate: () -> Unit = {}) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                val txHistory = publicStore.getTransactionHistory()
                val pending = txHistory.filter { it.status == TxStatus.PENDING }
                if (pending.isEmpty()) {
                    delay(15_000)
                    continue
                }

                for (tx in pending) {
                    try {
                        val client = chainManager.getClient(tx.chainId)
                        val receipt = client.getTransactionReceipt(tx.hash) ?: continue
                        val status = parseReceiptStatus(receipt)
                        if (status != null) {
                            publicStore.updateTransactionStatus(tx.hash, status)
                            onUpdate()
                        }
                    } catch (_: Exception) {
                        // Network error — will retry next cycle
                    }
                }

                delay(15_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun parseReceiptStatus(receipt: JsonObject): TxStatus? {
        val statusHex = receipt["status"]?.jsonPrimitive?.content ?: return null
        return when (statusHex) {
            "0x1" -> TxStatus.CONFIRMED
            "0x0" -> TxStatus.FAILED
            else -> null
        }
    }
}
