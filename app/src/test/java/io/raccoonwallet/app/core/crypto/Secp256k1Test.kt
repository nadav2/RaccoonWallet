package io.raccoonwallet.app.core.crypto

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class Secp256k1Test {

    @Test
    fun `generator point multiplication by 1 returns G`() {
        val result = Secp256k1.pointMultiplyG(BigInteger.ONE)
        assertEquals(Secp256k1.G.normalize(), result)
    }

    @Test
    fun `point addition is consistent with scalar multiplication`() {
        // (a+b)*G == a*G + b*G
        val a = BigInteger.valueOf(7)
        val b = BigInteger.valueOf(13)
        val left = Secp256k1.pointMultiplyG(a.add(b))
        val right = Secp256k1.pointAdd(
            Secp256k1.pointMultiplyG(a),
            Secp256k1.pointMultiplyG(b)
        )
        assertEquals(left, right)
    }

    @Test
    fun `compress then decompress round-trips`() {
        val keys = listOf(BigInteger.ONE, BigInteger.valueOf(42), Secp256k1.randomScalar())
        for (k in keys) {
            val point = Secp256k1.pointMultiplyG(k)
            val compressed = Secp256k1.compressPoint(point)
            val decompressed = Secp256k1.decompressPoint(compressed)
            assertEquals("Round-trip failed for k=$k", point, decompressed)
        }
    }

    @Test
    fun `compressed point is 33 bytes with correct prefix`() {
        val point = Secp256k1.pointMultiplyG(BigInteger.valueOf(42))
        val compressed = Secp256k1.compressPoint(point)
        assertEquals(33, compressed.size)
        assertTrue(
            "Prefix must be 0x02 or 0x03",
            compressed[0] == 0x02.toByte() || compressed[0] == 0x03.toByte()
        )
    }

    @Test
    fun `pointToEthAddress matches known vector for private key 1`() {
        // Private key 1 → well-known Ethereum address
        // Public key for k=1 is the generator point G
        val Q = Secp256k1.pointMultiplyG(BigInteger.ONE)
        val address = Secp256k1.pointToEthAddress(Q)

        // Known address for private key = 1 on secp256k1
        assertEquals("0x7e5f4552091a69125d5dfcb7b8c2659029395bdf", address)
    }

    @Test
    fun `scalarInverse is correct`() {
        val k = Secp256k1.randomScalar()
        val inv = Secp256k1.scalarInverse(k)
        val product = k.multiply(inv).mod(Secp256k1.N)
        assertEquals(BigInteger.ONE, product)
    }

    @Test
    fun `ecRecover recovers public key from standard ECDSA signature`() {
        // Create a standard ECDSA signature using the threshold protocol with known key
        val x = BigInteger.valueOf(12345)
        val Q = Secp256k1.pointMultiplyG(x)
        val hash = Keccak256.hash("ecrecover test".toByteArray())
        val h = BigInteger(1, hash)

        // Standard ECDSA sign: k random, R = k*G, r = R.x, s = k_inv * (h + r*x)
        val k = Secp256k1.randomScalar()
        val R = Secp256k1.pointMultiplyG(k)
        val r = R.affineXCoord.toBigInteger().mod(Secp256k1.N)
        val kInv = Secp256k1.scalarInverse(k)
        var s = kInv.multiply(h.add(r.multiply(x))).mod(Secp256k1.N)

        // Low-S
        if (s > Secp256k1.HALF_N) s = Secp256k1.N.subtract(s)

        // Try both recovery IDs
        val recovered0 = Secp256k1.ecRecover(r, s, h, 0)
        val recovered1 = Secp256k1.ecRecover(r, s, h, 1)

        assertTrue(
            "One recovery ID must produce the original public key",
            Q == recovered0 || Q == recovered1
        )
    }

    @Test
    fun `isOnCurve returns true for valid points`() {
        val point = Secp256k1.pointMultiplyG(Secp256k1.randomScalar())
        assertTrue(Secp256k1.isOnCurve(point))
    }

    @Test
    fun `address format is 0x plus 40 hex chars`() {
        val Q = Secp256k1.pointMultiplyG(Secp256k1.randomScalar())
        val address = Secp256k1.pointToEthAddress(Q)
        assertTrue(address.startsWith("0x"))
        assertEquals(42, address.length) // "0x" + 40 hex chars
    }
}
