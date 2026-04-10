package io.raccoonwallet.app.core.transport

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@OptIn(ExperimentalSerializationApi::class)
class MessageCodec {
    private val cbor = Cbor {
        ignoreUnknownKeys = true
    }

    fun encode(message: TransportMessage): ByteArray {
        return cbor.encodeToByteArray(message)
    }

    fun decode(data: ByteArray): TransportMessage {
        return cbor.decodeFromByteArray(data)
    }
}
