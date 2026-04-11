package io.raccoonwallet.app.core.crypto

import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/**
 * Lindell 2017 two-party ECDSA signing protocol.
 *
 * Vault (Party 1): holds x1, Paillier sk, initiates signing
 * Signer (Party 2): holds x2, ckey = Enc(pk, x1), computes partial sig homomorphically
 */
object LindellSign {

    /**
     * Vault: Prepare signing session. Generates ephemeral k1 and R1 = k1*G.
     */
    fun vaultPrepare(txHash: ByteArray): VaultSignPrepare {
        val k1 = Secp256k1.randomScalar()
        val R1 = Secp256k1.pointMultiplyG(k1)
        return VaultSignPrepare(
            k1 = k1,
            R1 = R1,
            txHash = txHash
        )
    }

    /**
     * Signer: Compute partial signature homomorphically.
     *
     * Given:
     *   - x2: Signer's key share
     *   - ckey: Enc(pk, x1)  [encrypted Vault share]
     *   - R1: Vault's ephemeral point
     *   - txHash: message hash
     *   - pk: Paillier public key
     *
     * Computes:
     *   - k2: random ephemeral
     *   - R = k2 * R1 = k1*k2*G
     *   - r = R.x mod n
     *   - c_partial = ckey^(k2_inv * r * x2) * g^(k2_inv * h) mod n^2
     *            = Enc(pk, k2_inv * r * x2 * x1 + k2_inv * h)
     *            = Enc(pk, k2_inv * (h + r * x))
     */
    fun signerComputePartial(
        x2: BigInteger,
        ckey: BigInteger,
        R1: ECPoint,
        txHash: ByteArray,
        paillierPk: PaillierCipher.PublicKey
    ): SignerPartialResult {
        require(Secp256k1.isOnCurve(R1)) { "R1 not on secp256k1 curve" }

        val n = Secp256k1.N
        val k2 = Secp256k1.randomScalar()
        val R2 = Secp256k1.pointMultiplyG(k2)

        // R = k2 * R1
        val R = Secp256k1.pointMultiply(R1, k2)
        val r = R.affineXCoord.toBigInteger().mod(n)
        require(r != BigInteger.ZERO) { "r is zero, retry" }

        val h = BigInteger(1, txHash).mod(n)
        val k2Inv = Secp256k1.scalarInverse(k2)

        // Homomorphic computation:
        // Step 1: ckey^(k2_inv * r * x2 mod n) = Enc(pk, x1 * k2_inv * r * x2)
        val exponent = k2Inv.multiply(r).multiply(x2).mod(n)
        val cStep1 = PaillierCipher.mulConstant(paillierPk, ckey, exponent)

        // Step 2: Add k2_inv * h to get Enc(pk, k2_inv * (h + r*x))
        val addend = k2Inv.multiply(h).mod(n)
        val cPartial = PaillierCipher.addConstant(paillierPk, cStep1, addend)

        return SignerPartialResult(
            R2 = R2,
            cPartial = cPartial,
            r = r
        )
    }

    /**
     * Vault: Finalize the signature.
     *
     * Given:
     *   - k1: Vault's ephemeral nonce
     *   - R2: Signer's ephemeral point
     *   - cPartial: Enc(pk, k2_inv * (h + r*x))
     *   - Paillier secret key
     *   - txHash: message hash
     *   - expectedPubKey: Q (for verification)
     *
     * Computes:
     *   - R = k1 * R2 = k1*k2*G
     *   - r = R.x mod n
     *   - s' = Dec(sk, cPartial) = k2_inv * (h + r*x)
     *   - s = k1_inv * s' = (k1*k2)_inv * (h + r*x) = k_inv * (h + r*x)
     *   - Normalize s to low-S (EIP-2)
     *   - Compute v recovery ID
     */
    fun vaultFinalize(
        k1: BigInteger,
        R2: ECPoint,
        cPartial: BigInteger,
        signerR: BigInteger,
        paillierSk: PaillierCipher.SecretKey,
        paillierPk: PaillierCipher.PublicKey,
        txHash: ByteArray,
        expectedPubKey: ECPoint
    ): EcdsaSignature {
        require(Secp256k1.isOnCurve(R2)) { "R2 not on secp256k1 curve" }

        val n = Secp256k1.N

        // R = k1 * R2
        val R = Secp256k1.pointMultiply(R2, k1)
        val r = R.affineXCoord.toBigInteger().mod(n)
        require(r != BigInteger.ZERO) { "r is zero" }

        // CRIT-2: Verify Vault's r matches Signer's r — prevents malicious partial sigs
        require(ConstantTime.equals(
            Hex.bigIntToBytesPadded(r, 32),
            Hex.bigIntToBytesPadded(signerR, 32)
        )) { "r mismatch between Vault and Signer" }

        // Decrypt partial signature
        val sPrime = PaillierCipher.decrypt(paillierSk, paillierPk, cPartial).mod(n)

        // Final s = k1_inv * s'
        val k1Inv = Secp256k1.scalarInverse(k1)
        var s = k1Inv.multiply(sPrime).mod(n)
        require(s != BigInteger.ZERO) { "s is zero" }

        // Low-S normalization (BIP-62 / EIP-2)
        if (s > Secp256k1.HALF_N) {
            s = n.subtract(s)
        }

        // Compute recovery ID v
        val v = recoverV(r, s, txHash, expectedPubKey)

        val sig = EcdsaSignature(r = r, s = s, v = v)

        // HIGH-5: Verify signature before returning — catches impl bugs
        require(EthSigner.verifySignature(txHash, sig, expectedPubKey)) {
            "Signature verification failed — ecrecover does not match expected public key"
        }

        return sig
    }

    /**
     * Determine the recovery ID (v) by trying both candidates and checking
     * which one recovers to the expected public key.
     */
    private fun recoverV(r: BigInteger, s: BigInteger, txHash: ByteArray, expectedQ: ECPoint): Int {
        val h = BigInteger(1, txHash)
        for (recId in 0..1) {
            val recovered = Secp256k1.ecRecover(r, s, h, recId)
            if (recovered != null && recovered == expectedQ) {
                return 27 + recId
            }
        }
        throw RuntimeException("Could not determine recovery ID")
    }

    data class VaultSignPrepare(
        val k1: BigInteger,
        val R1: ECPoint,
        val txHash: ByteArray
    ) {
        override fun equals(other: Any?) = other is VaultSignPrepare && k1 == other.k1
        override fun hashCode() = k1.hashCode()
    }

    data class SignerPartialResult(
        val R2: ECPoint,
        val cPartial: BigInteger,
        val r: BigInteger
    )

    data class EcdsaSignature(
        val r: BigInteger,
        val s: BigInteger,
        val v: Int
    )
}
