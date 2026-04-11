package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class LindellProtocolTest {

    companion object {
        private lateinit var paillierKp: PaillierCipher.KeyPair

        @BeforeClass
        @JvmStatic
        fun generatePaillierKey() {
            // Must be >= 1024 bits so Paillier n > secp256k1 curve order (256 bits).
            // 512-bit causes modular reduction errors in the homomorphic computation.
            paillierKp = PaillierCipher.generateKeyPair(bitLength = 1024)
        }
    }

    private val pk get() = paillierKp.publicKey
    private val sk get() = paillierKp.secretKey

    @Test
    fun `key split preserves multiplicative invariant`() {
        val x = Secp256k1.randomScalar()
        val result = LindellDkg.splitFromFullKey(x, pk)

        val reconstructed = result.x1.multiply(result.x2).mod(Secp256k1.N)
        assertEquals("x1 * x2 mod n must equal x", x, reconstructed)
    }

    @Test
    fun `split result public key matches original`() {
        val x = Secp256k1.randomScalar()
        val expectedQ = Secp256k1.pointMultiplyG(x)
        val result = LindellDkg.splitFromFullKey(x, pk)

        assertEquals(expectedQ, result.jointPublicKey)
    }

    @Test
    fun `split result address matches public key`() {
        val x = Secp256k1.randomScalar()
        val result = LindellDkg.splitFromFullKey(x, pk)

        val expectedAddress = Secp256k1.pointToEthAddress(result.jointPublicKey)
        assertEquals(expectedAddress, result.address)
    }

    @Test
    fun `paillier ckey decrypts to x1`() {
        val x = Secp256k1.randomScalar()
        val result = LindellDkg.splitFromFullKey(x, pk)

        val decryptedX1 = PaillierCipher.decrypt(sk, pk, result.ckey)
        assertEquals(result.x1, decryptedX1)
    }

    @Test
    fun `full sign flow produces valid ECDSA signature`() {
        val x = Secp256k1.randomScalar()
        val split = LindellDkg.splitFromFullKey(x, pk)
        val txHash = Keccak256.hash("test transaction".toByteArray())

        // Vault prepares
        val prep = LindellSign.vaultPrepare(txHash)

        // Signer computes partial
        val partial = LindellSign.signerComputePartial(
            x2 = split.x2,
            ckey = split.ckey,
            R1 = prep.R1,
            txHash = txHash,
            paillierPk = pk
        )

        // Vault finalizes
        val sig = LindellSign.vaultFinalize(
            k1 = prep.k1,
            R2 = partial.R2,
            cPartial = partial.cPartial,
            signerR = partial.r,
            paillierSk = sk,
            paillierPk = pk,
            txHash = txHash,
            expectedPubKey = split.jointPublicKey
        )

        // Verify ecrecover matches expected public key
        assertTrue(
            "CRIT-5: ecrecover must match expected public key",
            EthSigner.verifySignature(txHash, sig, split.jointPublicKey)
        )
    }

    @Test
    fun `CRIT-2 r values match between signer and vault`() {
        val x = Secp256k1.randomScalar()
        val split = LindellDkg.splitFromFullKey(x, pk)
        val txHash = Keccak256.hash("crit2 test".toByteArray())

        val prep = LindellSign.vaultPrepare(txHash)
        val partial = LindellSign.signerComputePartial(
            x2 = split.x2,
            ckey = split.ckey,
            R1 = prep.R1,
            txHash = txHash,
            paillierPk = pk
        )

        // Compute vault's r independently
        val R = Secp256k1.pointMultiply(partial.R2, prep.k1)
        val vaultR = R.affineXCoord.toBigInteger().mod(Secp256k1.N)

        assertEquals("CRIT-2: Vault r must equal Signer r", vaultR, partial.r)
    }

    @Test
    fun `low-S normalization is applied`() {
        // Run several times since the s value is random
        repeat(5) {
            val x = Secp256k1.randomScalar()
            val split = LindellDkg.splitFromFullKey(x, pk)
            val txHash = Keccak256.hash("lowS test $it".toByteArray())

            val prep = LindellSign.vaultPrepare(txHash)
            val partial = LindellSign.signerComputePartial(
                x2 = split.x2, ckey = split.ckey,
                R1 = prep.R1, txHash = txHash, paillierPk = pk
            )
            val sig = LindellSign.vaultFinalize(
                k1 = prep.k1, R2 = partial.R2,
                cPartial = partial.cPartial, signerR = partial.r,
                paillierSk = sk, paillierPk = pk,
                txHash = txHash, expectedPubKey = split.jointPublicKey
            )

            assertTrue("EIP-2: s must be <= n/2", sig.s <= Secp256k1.HALF_N)
        }
    }

    @Test
    fun `signing different messages produces different signatures`() {
        val x = Secp256k1.randomScalar()
        val split = LindellDkg.splitFromFullKey(x, pk)

        val hash1 = Keccak256.hash("message 1".toByteArray())
        val hash2 = Keccak256.hash("message 2".toByteArray())

        fun sign(hash: ByteArray): LindellSign.EcdsaSignature {
            val prep = LindellSign.vaultPrepare(hash)
            val partial = LindellSign.signerComputePartial(
                x2 = split.x2, ckey = split.ckey,
                R1 = prep.R1, txHash = hash, paillierPk = pk
            )
            return LindellSign.vaultFinalize(
                k1 = prep.k1, R2 = partial.R2,
                cPartial = partial.cPartial, signerR = partial.r,
                paillierSk = sk, paillierPk = pk,
                txHash = hash, expectedPubKey = split.jointPublicKey
            )
        }

        val sig1 = sign(hash1)
        val sig2 = sign(hash2)

        assertFalse(
            "Different messages should produce different r or s",
            sig1.r == sig2.r && sig1.s == sig2.s
        )
    }

    @Test
    fun `splitAllFromSeed processes multiple keys`() {
        val keys = (1..3).map { Secp256k1.randomScalar() }
        val results = LindellDkg.splitAllFromSeed(keys, pk)

        assertEquals(3, results.size)
        for ((i, result) in results.withIndex()) {
            val reconstructed = result.x1.multiply(result.x2).mod(Secp256k1.N)
            assertEquals("Key $i split failed", keys[i], reconstructed)
        }
    }

    @Test
    fun `v is 27 or 28`() {
        val x = Secp256k1.randomScalar()
        val split = LindellDkg.splitFromFullKey(x, pk)
        val txHash = Keccak256.hash("v test".toByteArray())

        val prep = LindellSign.vaultPrepare(txHash)
        val partial = LindellSign.signerComputePartial(
            x2 = split.x2, ckey = split.ckey,
            R1 = prep.R1, txHash = txHash, paillierPk = pk
        )
        val sig = LindellSign.vaultFinalize(
            k1 = prep.k1, R2 = partial.R2,
            cPartial = partial.cPartial, signerR = partial.r,
            paillierSk = sk, paillierPk = pk,
            txHash = txHash, expectedPubKey = split.jointPublicKey
        )

        assertTrue("v must be 27 or 28", sig.v == 27 || sig.v == 28)
    }
}
