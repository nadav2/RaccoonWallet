package io.raccoonwallet.app.core.crypto

import java.io.ByteArrayOutputStream
import java.math.BigInteger

/**
 * Recursive Length Prefix (RLP) encoder for Ethereum transaction serialization.
 * Spec: https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/
 */
object Rlp {

    private const val STRING_SHORT_PREFIX = 0x80
    private const val STRING_LONG_PREFIX = 0xB7
    private const val LIST_SHORT_PREFIX = 0xC0
    private const val LIST_LONG_PREFIX = 0xF7

    sealed class Item {
        class Bytes(val data: ByteArray) : Item()
        data class RlpList(val items: List<Item>) : Item()
    }

    fun encode(item: Item): ByteArray {
        return when (item) {
            is Item.Bytes -> encodeBytes(item.data)
            is Item.RlpList -> encodeList(item.items)
        }
    }

    fun encodeBytes(data: ByteArray): ByteArray {
        return when {
            data.size == 1 && data[0].toInt() and 0xFF < STRING_SHORT_PREFIX -> data
            data.size <= 55 -> byteArrayOf((STRING_SHORT_PREFIX + data.size).toByte()) + data
            else -> {
                val lenBytes = toMinimalBytes(data.size.toLong())
                byteArrayOf((STRING_LONG_PREFIX + lenBytes.size).toByte()) + lenBytes + data
            }
        }
    }

    fun encodeList(items: List<Item>): ByteArray {
        val payload = ByteArrayOutputStream()
        for (item in items) {
            payload.write(encode(item))
        }
        val data = payload.toByteArray()
        return when {
            data.size <= 55 -> byteArrayOf((LIST_SHORT_PREFIX + data.size).toByte()) + data
            else -> {
                val lenBytes = toMinimalBytes(data.size.toLong())
                byteArrayOf((LIST_LONG_PREFIX + lenBytes.size).toByte()) + lenBytes + data
            }
        }
    }

    fun encodeBigInt(value: BigInteger): Item = Item.Bytes(Hex.bigIntToBytes(value))

    fun encodeLong(value: Long): Item {
        if (value == 0L) return Item.Bytes(byteArrayOf())
        return Item.Bytes(toMinimalBytes(value))
    }

    fun encodeHex(hex: String): Item {
        val clean = Hex.stripPrefix(hex)
        if (clean.isEmpty()) return Item.Bytes(byteArrayOf())
        return Item.Bytes(Hex.decode(clean))
    }

    fun encodeAddress(address: String): Item {
        val clean = Hex.stripPrefix(address)
        require(clean.length == 40) { "Invalid address length" }
        return Item.Bytes(Hex.decode(clean))
    }

    private fun toMinimalBytes(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf()
        val buf = ByteArray(8)
        var v = value
        var i = 7
        while (v > 0) {
            buf[i--] = (v and 0xFF).toByte()
            v = v shr 8
        }
        return buf.copyOfRange(i + 1, 8)
    }
}
