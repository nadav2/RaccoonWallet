package io.raccoonwallet.app.core.crypto

import java.math.BigDecimal
import java.math.BigInteger

object Hex {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX_CHARS[(b.toInt() shr 4) and 0x0F])
            sb.append(HEX_CHARS[b.toInt() and 0x0F])
        }
        return sb.toString()
    }

    fun encodeWithPrefix(bytes: ByteArray): String = "0x${encode(bytes)}"

    fun stripPrefix(hex: String): String = hex.removePrefix("0x").removePrefix("0X")

    fun decode(hex: String): ByteArray {
        val clean = stripPrefix(hex)
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { i ->
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character at position ${i * 2}" }
            ((hi shl 4) or lo).toByte()
        }
    }

    fun bigIntToHex(value: BigInteger): String = "0x${value.toString(16)}"

    fun hexToBigInt(hex: String): BigInteger = BigInteger(hex.removePrefix("0x").removePrefix("0X"), 16)

    /** Encode BigInteger as minimal byte array (no leading zeros, but at least 1 byte for zero). */
    fun bigIntToBytes(value: BigInteger): ByteArray {
        if (value == BigInteger.ZERO) return byteArrayOf()
        val bytes = value.toByteArray()
        // Strip leading zero byte added by BigInteger for sign
        return if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
    }

    /** Pad BigInteger to exactly n bytes. */
    fun bigIntToBytesPadded(value: BigInteger, length: Int): ByteArray {
        val bytes = bigIntToBytes(value)
        require(bytes.size <= length) { "Value too large for $length bytes" }
        return ByteArray(length - bytes.size) + bytes
    }

    /** Convert wei (BigInteger) to human-readable ether string. */
    fun weiToEther(wei: BigInteger, decimals: Int = 18, scale: Int = 6): BigDecimal {
        val divisor = BigDecimal.TEN.pow(decimals)
        return BigDecimal(wei).divide(divisor, scale, BigDecimal.ROUND_HALF_UP)
    }

    /** Convert ether (decimal string) to wei. LOW-2: use setScale to avoid precision loss. */
    fun etherToWei(ether: String, decimals: Int = 18): BigInteger {
        val multiplier = BigDecimal.TEN.pow(decimals)
        val wei = BigDecimal(ether).multiply(multiplier).setScale(0, java.math.RoundingMode.DOWN)
        return wei.toBigIntegerExact()
    }
}
