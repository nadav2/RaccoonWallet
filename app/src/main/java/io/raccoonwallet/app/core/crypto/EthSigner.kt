package io.raccoonwallet.app.core.crypto

import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/**
 * Bridge between the Lindell 2017 threshold protocol and EVM transaction encoding.
 */
object EthSigner {

    fun buildTransaction(
        nonce: BigInteger,
        chainId: Long,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        gasLimit: BigInteger,
        to: String,
        value: BigInteger,
        data: ByteArray = byteArrayOf()
    ): EthTransaction {
        return EthTransaction(chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data)
    }

    /**
     * Verify that ecrecover(hash, signature) matches the expected public key.
     */
    fun verifySignature(
        txHash: ByteArray,
        signature: LindellSign.EcdsaSignature,
        expectedPubKey: ECPoint
    ): Boolean {
        return try {
            val recovered = Secp256k1.ecRecover(
                r = signature.r,
                s = signature.s,
                h = BigInteger(1, txHash),
                recId = signature.v - 27
            )
            recovered != null && recovered == expectedPubKey
        } catch (_: Exception) {
            false
        }
    }
}
