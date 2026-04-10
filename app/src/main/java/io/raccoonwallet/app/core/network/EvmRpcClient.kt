package io.raccoonwallet.app.core.network

import io.raccoonwallet.app.core.crypto.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

class BroadcastException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class EvmRpcClient(
    private val rpc: JsonRpc
) {
    suspend fun getBalance(address: String): BigInteger = withContext(Dispatchers.IO) {
        val result = rpc.call("eth_getBalance", JsonPrimitive(address), JsonPrimitive("latest"))
        Hex.hexToBigInt(result.jsonPrimitive.content)
    }

    suspend fun getNonce(address: String): BigInteger = withContext(Dispatchers.IO) {
        val result = rpc.call("eth_getTransactionCount", JsonPrimitive(address), JsonPrimitive("pending"))
        Hex.hexToBigInt(result.jsonPrimitive.content)
    }

    suspend fun estimateGas(from: String, to: String, value: BigInteger, data: String = "0x"): BigInteger =
        withContext(Dispatchers.IO) {
            val txObj = buildJsonObject {
                put("from", JsonPrimitive(from))
                put("to", JsonPrimitive(to))
                put("value", JsonPrimitive(Hex.bigIntToHex(value)))
                if (data != "0x") put("data", JsonPrimitive(data))
            }
            val result = rpc.call("eth_estimateGas", txObj)
            Hex.hexToBigInt(result.jsonPrimitive.content)
        }

    suspend fun getBaseFee(): BigInteger = withContext(Dispatchers.IO) {
        try {
            val result = rpc.call("eth_getBlockByNumber", JsonPrimitive("latest"), JsonPrimitive(false))
            val block = result as kotlinx.serialization.json.JsonObject
            val baseFee = block["baseFeePerGas"]?.jsonPrimitive?.content ?: "0x0"
            Hex.hexToBigInt(baseFee)
        } catch (_: Exception) {
            BigInteger.ZERO
        }
    }

    suspend fun getMaxPriorityFeePerGas(): BigInteger = withContext(Dispatchers.IO) {
        try {
            val result = rpc.call("eth_maxPriorityFeePerGas")
            Hex.hexToBigInt(result.jsonPrimitive.content)
        } catch (_: Exception) {
            BigInteger("1500000000") // 1.5 Gwei fallback
        }
    }

    suspend fun sendRawTransaction(signedHex: String): String = withContext(Dispatchers.IO) {
        try {
            val result = rpc.call("eth_sendRawTransaction", JsonPrimitive(signedHex))
            result.jsonPrimitive.content
        } catch (e: Exception) {
            throw BroadcastException(e.message ?: "Transaction broadcast failed", e)
        }
    }

    suspend fun getTransactionReceipt(txHash: String): kotlinx.serialization.json.JsonObject? =
        withContext(Dispatchers.IO) {
            val result = rpc.call("eth_getTransactionReceipt", JsonPrimitive(txHash))
            result as? kotlinx.serialization.json.JsonObject
        }

    suspend fun ethCall(to: String, data: String): String = withContext(Dispatchers.IO) {
        val callObj = buildJsonObject {
            put("to", JsonPrimitive(to))
            put("data", JsonPrimitive(data))
        }
        val result = rpc.call("eth_call", callObj, JsonPrimitive("latest"))
        result.jsonPrimitive.content
    }
}
