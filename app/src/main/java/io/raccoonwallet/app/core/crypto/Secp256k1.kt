package io.raccoonwallet.app.core.crypto

import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import java.math.BigInteger
import java.security.SecureRandom

object Secp256k1 {
    private val CURVE_PARAMS: X9ECParameters = CustomNamedCurves.getByName("secp256k1")

    val CURVE = ECDomainParameters(
        CURVE_PARAMS.curve,
        CURVE_PARAMS.g,
        CURVE_PARAMS.n,
        CURVE_PARAMS.h
    )

    val G: ECPoint = CURVE.g
    val N: BigInteger = CURVE.n
    val HALF_N: BigInteger = N.shiftRight(1)

    private val secureRandom = SecureRandom()

    fun randomScalar(): BigInteger {
        var k: BigInteger
        do {
            k = BigInteger(N.bitLength(), secureRandom)
        } while (k >= N || k == BigInteger.ZERO)
        return k
    }

    fun pointMultiplyG(k: BigInteger): ECPoint {
        return FixedPointCombMultiplier().multiply(G, k).normalize()
    }

    fun pointMultiply(P: ECPoint, k: BigInteger): ECPoint {
        return P.multiply(k).normalize()
    }

    fun pointAdd(P: ECPoint, Q: ECPoint): ECPoint {
        return P.add(Q).normalize()
    }

    fun isOnCurve(P: ECPoint): Boolean {
        return try {
            !P.isInfinity && P.isValid
        } catch (_: Exception) {
            false
        }
    }

    fun compressPoint(P: ECPoint): ByteArray {
        return P.getEncoded(true)
    }

    fun decompressPoint(encoded: ByteArray): ECPoint {
        return CURVE.curve.decodePoint(encoded).normalize()
    }

    fun pointToEthAddress(Q: ECPoint): String {
        val uncompressed = Q.getEncoded(false)
        // Drop the 0x04 prefix byte
        val pubBytes = uncompressed.copyOfRange(1, uncompressed.size)
        val hash = Keccak256.hash(pubBytes)
        // Last 20 bytes
        val addressBytes = hash.copyOfRange(hash.size - 20, hash.size)
        return "0x${Hex.encode(addressBytes)}"
    }

    fun scalarInverse(k: BigInteger): BigInteger {
        return k.modInverse(N)
    }

    /**
     * Recover public key from ECDSA signature components.
     * Single implementation used by both LindellSign and EthSigner.
     */
    fun ecRecover(r: BigInteger, s: BigInteger, h: BigInteger, recId: Int): ECPoint? {
        val rPoint = try {
            val xBytes = Hex.bigIntToBytesPadded(r, 32)
            val prefix = if (recId % 2 == 0) 0x02.toByte() else 0x03.toByte()
            decompressPoint(byteArrayOf(prefix) + xBytes)
        } catch (_: Exception) {
            return null
        }

        if (!isOnCurve(rPoint)) return null

        val rInv = r.modInverse(N)
        val u1 = h.negate().mod(N).multiply(rInv).mod(N)
        val u2 = s.multiply(rInv).mod(N)

        val q = pointAdd(pointMultiplyG(u1), pointMultiply(rPoint, u2))
        return if (q.isInfinity) null else q
    }
}
