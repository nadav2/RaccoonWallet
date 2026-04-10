package io.raccoonwallet.app.core.crypto

import java.math.BigInteger
import java.security.SecureRandom

object PaillierCipher {

    data class PublicKey(
        val n: BigInteger,
        val nSquared: BigInteger,
        val g: BigInteger
    )

    data class SecretKey(
        val lambda: BigInteger,
        val mu: BigInteger
    )

    data class KeyPair(
        val publicKey: PublicKey,
        val secretKey: SecretKey
    )

    private val secureRandom = SecureRandom()
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger.valueOf(2)

    /**
     * Generate a Paillier keypair with the given bit length for the modulus n.
     * This is the slow operation (~5-15s for 2048 bits). Run off the main thread.
     */
    fun generateKeyPair(bitLength: Int = 2048): KeyPair {
        val halfBits = bitLength / 2

        var p: BigInteger
        var q: BigInteger
        var n: BigInteger
        var lambda: BigInteger

        // Generate p, q such that gcd(p*q, (p-1)*(q-1)) == 1
        do {
            p = BigInteger.probablePrime(halfBits, secureRandom)
            q = BigInteger.probablePrime(halfBits, secureRandom)
            n = p.multiply(q)
            lambda = lcm(p.subtract(ONE), q.subtract(ONE))
        } while (p == q || n.bitLength() != bitLength ||
            lambda.gcd(n) != ONE)

        val nSquared = n.multiply(n)
        val g = n.add(ONE) // Simplified generator g = n + 1

        // mu = L(g^lambda mod n^2)^-1 mod n
        // where L(x) = (x - 1) / n
        val gLambda = g.modPow(lambda, nSquared)
        val lValue = lFunction(gLambda, n)

        // Verify lValue is invertible mod n
        require(lValue.gcd(n) == ONE) { "L(g^lambda) not invertible mod n, regenerate keys" }

        val mu = lValue.modInverse(n)

        return KeyPair(
            publicKey = PublicKey(n = n, nSquared = nSquared, g = g),
            secretKey = SecretKey(lambda = lambda, mu = mu)
        )
    }

    /**
     * Encrypt plaintext m under public key pk.
     * c = g^m * r^n mod n^2, where r is random in Z*_n
     */
    fun encrypt(pk: PublicKey, m: BigInteger): BigInteger {
        val r = randomInZStarN(pk.n)
        return encrypt(pk, m, r)
    }

    fun encrypt(pk: PublicKey, m: BigInteger, r: BigInteger): BigInteger {
        // g^m mod n^2
        val gm = pk.g.modPow(m.mod(pk.n), pk.nSquared)
        // r^n mod n^2
        val rn = r.modPow(pk.n, pk.nSquared)
        // c = g^m * r^n mod n^2
        return gm.multiply(rn).mod(pk.nSquared)
    }

    /**
     * Decrypt ciphertext c using secret key sk and public key pk.
     * m = L(c^lambda mod n^2) * mu mod n
     *
     * Uses RSA blinding to mitigate timing side-channels on BigInteger.modPow.
     */
    fun decrypt(sk: SecretKey, pk: PublicKey, c: BigInteger): BigInteger {
        // Blinding: multiply c by Enc(0) = r^n mod n^2 to randomize modPow input
        val blindR = randomInZStarN(pk.n)
        val blindFactor = blindR.modPow(pk.n, pk.nSquared)
        val blindedC = c.multiply(blindFactor).mod(pk.nSquared)

        // Decrypt blinded ciphertext (result = m + 0 = m since we added Enc(0))
        val blindedLambda = blindedC.modPow(sk.lambda, pk.nSquared)
        val lValue = lFunction(blindedLambda, pk.n)
        return lValue.multiply(sk.mu).mod(pk.n)
    }

    /**
     * Homomorphic addition: Enc(m1) * Enc(m2) = Enc(m1 + m2) mod n^2
     */
    fun addCiphertexts(pk: PublicKey, c1: BigInteger, c2: BigInteger): BigInteger {
        return c1.multiply(c2).mod(pk.nSquared)
    }

    /**
     * Homomorphic scalar multiplication: Enc(m)^k = Enc(k * m) mod n^2
     */
    fun mulConstant(pk: PublicKey, c: BigInteger, k: BigInteger): BigInteger {
        return c.modPow(k.mod(pk.n), pk.nSquared)
    }

    /**
     * Add a plaintext constant: Enc(m) * g^k = Enc(m + k) mod n^2
     */
    fun addConstant(pk: PublicKey, c: BigInteger, k: BigInteger): BigInteger {
        val gk = pk.g.modPow(k.mod(pk.n), pk.nSquared)
        return c.multiply(gk).mod(pk.nSquared)
    }

    // L(x) = (x - 1) / n
    private fun lFunction(x: BigInteger, n: BigInteger): BigInteger {
        return x.subtract(ONE).divide(n)
    }

    // lcm(a, b) = a * b / gcd(a, b)
    private fun lcm(a: BigInteger, b: BigInteger): BigInteger {
        return a.multiply(b).divide(a.gcd(b))
    }

    // Random element in Z*_n (coprime to n)
    private fun randomInZStarN(n: BigInteger): BigInteger {
        var r: BigInteger
        do {
            r = BigInteger(n.bitLength(), secureRandom).mod(n)
        } while (r == BigInteger.ZERO || r.gcd(n) != ONE)
        return r
    }
}
