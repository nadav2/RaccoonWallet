package io.raccoonwallet.app.core.crypto

import java.math.BigInteger

/**
 * EIP-1559 transaction builder and serializer.
 * Replaces web3j's RawTransaction + TransactionEncoder.
 *
 * Signed format: 0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
 *                              gasLimit, to, value, data, accessList,
 *                              yParity, r, s])
 */
data class EthTransaction(
    val chainId: Long,
    val nonce: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    val maxFeePerGas: BigInteger,
    val gasLimit: BigInteger,
    val to: String,
    val value: BigInteger,
    val data: ByteArray = byteArrayOf()
) {
    /**
     * Compute the hash to be signed (keccak256 of 0x02 || RLP(unsigned fields)).
     */
    fun signingHash(): ByteArray {
        val rlpEncoded = Rlp.encode(
            Rlp.Item.RlpList(unsignedFields())
        )
        // EIP-2718 typed transaction: hash(0x02 || rlp_payload)
        val envelope = byteArrayOf(0x02) + rlpEncoded
        return Keccak256.hash(envelope)
    }

    /**
     * Encode the signed transaction as a hex string ready for eth_sendRawTransaction.
     */
    fun encodeSignedHex(signature: LindellSign.EcdsaSignature): String {
        val yParity = signature.v - 27 // EIP-1559 uses 0/1, not 27/28

        val fields = unsignedFields() + listOf(
            Rlp.encodeLong(yParity.toLong()),
            Rlp.encodeBigInt(signature.r),
            Rlp.encodeBigInt(signature.s)
        )

        val rlpEncoded = Rlp.encode(Rlp.Item.RlpList(fields))
        val envelope = byteArrayOf(0x02) + rlpEncoded
        return Hex.encodeWithPrefix(envelope)
    }

    private fun unsignedFields(): List<Rlp.Item> {
        return listOf(
            Rlp.encodeLong(chainId),
            Rlp.encodeBigInt(nonce),
            Rlp.encodeBigInt(maxPriorityFeePerGas),
            Rlp.encodeBigInt(maxFeePerGas),
            Rlp.encodeBigInt(gasLimit),
            Rlp.encodeAddress(to),
            Rlp.encodeBigInt(value),
            Rlp.Item.Bytes(data),
            Rlp.Item.RlpList(emptyList()) // accessList (empty)
        )
    }

    override fun equals(other: Any?) =
        other is EthTransaction && chainId == other.chainId && nonce == other.nonce
    override fun hashCode() = nonce.hashCode()
}
