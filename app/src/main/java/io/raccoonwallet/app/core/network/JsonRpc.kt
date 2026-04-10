package io.raccoonwallet.app.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal JSON-RPC 2.0 client using HttpURLConnection (no OkHttp dependency).
 */
class JsonRpc(private val rpcUrl: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val requestId = AtomicLong(1)

    fun call(method: String, vararg params: JsonElement): JsonElement {
        val body = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
            put("params", buildJsonArray { params.forEach { add(it) } })
            put("id", JsonPrimitive(requestId.getAndIncrement()))
        }

        val conn = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
        }

        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val response = json.parseToJsonElement(responseText).jsonObject

            val error = response["error"]
            if (error != null && error is JsonObject) {
                val message = error["message"]?.jsonPrimitive?.content ?: "Unknown RPC error"
                throw RuntimeException("RPC error: $message")
            }

            return response["result"] ?: throw RuntimeException("No result in RPC response")
        } finally {
            conn.disconnect()
        }
    }
}
