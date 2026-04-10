package io.raccoonwallet.app.core.network

import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.crypto.Keccak256
import java.math.BigInteger

/**
 * Minimal ABI encoding helpers for ERC-20 balanceOf and transfer calls.
 */
object Erc20Abi {

    /** keccak256("balanceOf(address)") first 4 bytes */
    private val BALANCE_OF_SELECTOR: ByteArray by lazy {
        Keccak256.hash("balanceOf(address)".toByteArray(Charsets.US_ASCII)).copyOfRange(0, 4)
    }

    /** keccak256("transfer(address,uint256)") first 4 bytes */
    private val TRANSFER_SELECTOR: ByteArray by lazy {
        Keccak256.hash("transfer(address,uint256)".toByteArray(Charsets.US_ASCII)).copyOfRange(0, 4)
    }

    /**
     * Encode an `eth_call` data payload for `balanceOf(address)`.
     * Returns a hex string with 0x prefix (4 + 32 = 36 bytes = 72 hex chars + "0x").
     */
    fun encodeBalanceOf(ownerAddress: String): String {
        val addrBytes = Hex.decode(Hex.stripPrefix(ownerAddress))
        require(addrBytes.size <= 20) { "Address too long: ${addrBytes.size} bytes (expected 20)" }
        val padded = ByteArray(32 - addrBytes.size) + addrBytes
        return "0x" + Hex.encode(BALANCE_OF_SELECTOR) + Hex.encode(padded)
    }

    /**
     * Encode an `eth_call` / transaction data payload for `transfer(address,uint256)`.
     * Returns a hex string with 0x prefix (4 + 32 + 32 = 68 bytes).
     */
    fun encodeTransfer(toAddress: String, amount: BigInteger): String {
        val addrBytes = Hex.decode(Hex.stripPrefix(toAddress))
        require(addrBytes.size <= 20) { "Address too long: ${addrBytes.size} bytes (expected 20)" }
        val paddedAddr = ByteArray(32 - addrBytes.size) + addrBytes
        val paddedAmount = Hex.bigIntToBytesPadded(amount, 32)
        return "0x" + Hex.encode(TRANSFER_SELECTOR) + Hex.encode(paddedAddr) + Hex.encode(paddedAmount)
    }

    /**
     * Decode a hex-encoded uint256 result from an `eth_call` response.
     */
    fun decodeUint256(hexResult: String): BigInteger {
        val clean = Hex.stripPrefix(hexResult).ifEmpty { "0" }
        return BigInteger(clean, 16)
    }
}
