package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigInteger

class PaillierCipherTest {

    companion object {
        private lateinit var kp: PaillierCipher.KeyPair

        @BeforeClass
        @JvmStatic
        fun generateKey() {
            // Use 512-bit key for speed in tests (production uses 2048)
            kp = PaillierCipher.generateKeyPair(bitLength = 512)
        }
    }

    private val pk get() = kp.publicKey
    private val sk get() = kp.secretKey

    @Test
    fun `encrypt then decrypt round-trips`() {
        val values = listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger.valueOf(42),
            BigInteger.valueOf(1_000_000),
            pk.n.subtract(BigInteger.ONE) // n-1
        )
        for (m in values) {
            val c = PaillierCipher.encrypt(pk, m)
            val decrypted = PaillierCipher.decrypt(sk, pk, c)
            assertEquals("Round-trip failed for m=$m", m, decrypted)
        }
    }

    @Test
    fun `encryption is randomized`() {
        val m = BigInteger.valueOf(42)
        val c1 = PaillierCipher.encrypt(pk, m)
        val c2 = PaillierCipher.encrypt(pk, m)
        assertNotEquals("Same plaintext should produce different ciphertexts", c1, c2)
        // But both decrypt to the same value
        assertEquals(m, PaillierCipher.decrypt(sk, pk, c1))
        assertEquals(m, PaillierCipher.decrypt(sk, pk, c2))
    }

    @Test
    fun `homomorphic addition of ciphertexts`() {
        val a = BigInteger.valueOf(100)
        val b = BigInteger.valueOf(200)
        val ca = PaillierCipher.encrypt(pk, a)
        val cb = PaillierCipher.encrypt(pk, b)

        val cSum = PaillierCipher.addCiphertexts(pk, ca, cb)
        val decrypted = PaillierCipher.decrypt(sk, pk, cSum)
        assertEquals(a.add(b), decrypted)
    }

    @Test
    fun `homomorphic scalar multiplication`() {
        val m = BigInteger.valueOf(7)
        val k = BigInteger.valueOf(6)
        val cm = PaillierCipher.encrypt(pk, m)

        val cProduct = PaillierCipher.mulConstant(pk, cm, k)
        val decrypted = PaillierCipher.decrypt(sk, pk, cProduct)
        assertEquals(m.multiply(k), decrypted)
    }

    @Test
    fun `addConstant adds plaintext to encrypted value`() {
        val m = BigInteger.valueOf(50)
        val k = BigInteger.valueOf(30)
        val cm = PaillierCipher.encrypt(pk, m)

        val cResult = PaillierCipher.addConstant(pk, cm, k)
        val decrypted = PaillierCipher.decrypt(sk, pk, cResult)
        assertEquals(m.add(k), decrypted)
    }

    @Test
    fun `mulConstant then addConstant composes correctly`() {
        // Enc(m) -> mulConstant(k1) -> addConstant(k2) -> Dec = m*k1 + k2
        val m = BigInteger.valueOf(10)
        val k1 = BigInteger.valueOf(5)
        val k2 = BigInteger.valueOf(3)
        val cm = PaillierCipher.encrypt(pk, m)

        val step1 = PaillierCipher.mulConstant(pk, cm, k1)
        val step2 = PaillierCipher.addConstant(pk, step1, k2)
        val decrypted = PaillierCipher.decrypt(sk, pk, step2)
        assertEquals(m.multiply(k1).add(k2), decrypted)
    }

    @Test
    fun `key generation produces valid parameters`() {
        assertEquals("g should be n+1", pk.n.add(BigInteger.ONE), pk.g)
        assertEquals("nSquared should be n*n", pk.n.multiply(pk.n), pk.nSquared)
        assertTrue("n should be at least 512 bits", pk.n.bitLength() >= 512)
    }

    @Test
    fun `deterministic encryption with fixed r`() {
        val m = BigInteger.valueOf(42)
        val r = BigInteger.valueOf(17) // Fixed r
        val c1 = PaillierCipher.encrypt(pk, m, r)
        val c2 = PaillierCipher.encrypt(pk, m, r)
        assertEquals("Same (m, r) should produce same ciphertext", c1, c2)
    }
}
