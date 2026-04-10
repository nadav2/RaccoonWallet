package io.raccoonwallet.app.core.crypto

import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger

/**
 * Seed-based key splitting for 2-of-2 threshold ECDSA (Lindell 2017).
 *
 * The Signer derives full private keys from a BIP39 seed, splits each
 * multiplicatively: x = x1 * x2 mod n. Vault gets x1 + Paillier sk,
 * Signer keeps x2 + ckey + Paillier pk.
 */
object LindellDkg {

    /**
     * Split a full private key into multiplicative threshold shares.
     * Used when importing from BIP39 seed — Vault derives the full key,
     * then splits it so neither party holds the complete key afterward.
     *
     * x1 = random, x2 = x * x1_inv mod n, so x1 * x2 = x mod n
     * ckey = Enc(pk, x1) for Paillier homomorphic signing
     */
    data class SplitResult(
        val x1: BigInteger,
        val x2: BigInteger,
        val ckey: BigInteger,
        val jointPublicKey: ECPoint,
        val address: String
    )

    fun splitFromFullKey(
        privateKey: BigInteger,
        paillierPk: PaillierCipher.PublicKey
    ): SplitResult {
        require(privateKey > BigInteger.ZERO && privateKey < Secp256k1.N) {
            "Private key out of range"
        }

        val x1 = Secp256k1.randomScalar()
        val x1Inv = Secp256k1.scalarInverse(x1)
        val x2 = privateKey.multiply(x1Inv).mod(Secp256k1.N)

        // Verify: x1 * x2 == privateKey mod n
        val reconstructed = x1.multiply(x2).mod(Secp256k1.N)
        require(reconstructed == privateKey) { "Share splitting verification failed" }

        val jointPublicKey = Secp256k1.pointMultiplyG(privateKey)
        val ckey = PaillierCipher.encrypt(paillierPk, x1)
        val address = Secp256k1.pointToEthAddress(jointPublicKey)

        return SplitResult(
            x1 = x1,
            x2 = x2,
            ckey = ckey,
            jointPublicKey = jointPublicKey,
            address = address
        )
    }

    /**
     * Split multiple private keys (from BIP44 derivation) into threshold shares.
     */
    fun splitAllFromSeed(
        privateKeys: List<BigInteger>,
        paillierPk: PaillierCipher.PublicKey
    ): List<SplitResult> {
        return privateKeys.mapIndexed { _, key -> splitFromFullKey(key, paillierPk) }
    }

}
