package io.raccoonwallet.app.core.transport

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ECDH key exchange + AES-256-GCM session encryption for NFC transport.
 * Uses P-256 for ECDH (transport security, not signing).
 */
class SessionCrypto {

    private var sessionKey: ByteArray? = null
    private var encryptCounter: Long = 0
    private var decryptCounter: Long = 0
    // HIGH-2: Increased salt to 16 bytes
    private var sessionSalt: ByteArray = ByteArray(16)

    private var ephemeralPrivateKey: ECPrivateKey? = null

    fun initiateHandshake(): ByteArray {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val kp = kpg.generateKeyPair()
        ephemeralPrivateKey = kp.private as ECPrivateKey

        SecureRandom().nextBytes(sessionSalt)

        // Return salt + ephemeral public key so both sides share the salt
        val pubBytes = encodeEcPublicKey(kp.public as ECPublicKey)
        return sessionSalt + pubBytes
    }

    fun completeHandshake(otherPayload: ByteArray) {
        // Parse salt + public key from other side
        val otherSalt = otherPayload.copyOfRange(0, 16)
        val otherPubKeyBytes = otherPayload.copyOfRange(16, otherPayload.size)

        // Combine both salts (XOR) so both sides contribute
        val combinedSalt = ByteArray(16)
        for (i in 0 until 16) {
            combinedSalt[i] = (sessionSalt[i].toInt() xor otherSalt[i].toInt()).toByte()
        }
        sessionSalt = combinedSalt

        val otherPubKey = decodeEcPublicKey(otherPubKeyBytes)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephemeralPrivateKey)
        ka.doPhase(otherPubKey, true)
        val sharedSecret = ka.generateSecret()

        sessionKey = hkdfSha256(sharedSecret, combinedSalt, "raccoonwallet-nfc".toByteArray(), 32)
        ephemeralPrivateKey = null
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session not established")
        val nonce = buildNonce(encryptCounter++)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    fun decrypt(data: ByteArray): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session not established")
        require(data.size > 12) { "Encrypted data too short" }
        val receivedNonce = data.copyOfRange(0, 12)

        // CRIT-4: Validate nonce matches expected counter — prevents replay attacks
        val expectedNonce = buildNonce(decryptCounter)
        require(receivedNonce.contentEquals(expectedNonce)) {
            "Nonce mismatch — possible replay attack (expected counter $decryptCounter)"
        }

        val ciphertext = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, receivedNonce))
        val plaintext = cipher.doFinal(ciphertext)
        decryptCounter++ // Only increment after successful decryption
        return plaintext
    }

    fun isEstablished(): Boolean = sessionKey != null

    fun reset() {
        sessionKey?.fill(0)
        sessionKey = null
        encryptCounter = 0
        decryptCounter = 0
        sessionSalt = ByteArray(16)
        ephemeralPrivateKey = null
    }

    private fun buildNonce(counter: Long): ByteArray {
        // 4 bytes salt prefix + 8 bytes counter
        return ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
            .put(sessionSalt, 0, 4) // first 4 bytes of salt as nonce prefix
            .putLong(counter)
            .array()
    }

    private fun encodeEcPublicKey(key: ECPublicKey): ByteArray {
        val x = key.w.affineX.toByteArray().takeLast(32).toByteArray()
        val y = key.w.affineY.toByteArray().takeLast(32).toByteArray()
        val result = ByteArray(65)
        result[0] = 0x04
        x.copyInto(result, 1 + (32 - x.size))
        y.copyInto(result, 33 + (32 - y.size))
        return result
    }

    private fun decodeEcPublicKey(encoded: ByteArray): ECPublicKey {
        val kf = java.security.KeyFactory.getInstance("EC")
        val spec = java.security.spec.ECPublicKeySpec(
            java.security.spec.ECPoint(
                java.math.BigInteger(1, encoded.copyOfRange(1, 33)),
                java.math.BigInteger(1, encoded.copyOfRange(33, 65))
            ),
            p256Params
        )
        return kf.generatePublic(spec) as ECPublicKey
    }

    companion object {
        // #5: Cache P-256 params — no key generation needed
        private val p256Params: java.security.spec.ECParameterSpec by lazy {
            val params = java.security.AlgorithmParameters.getInstance("EC")
            params.init(ECGenParameterSpec("secp256r1"))
            params.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        }
    }

    private fun hkdfSha256(
        inputKey: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(inputKey)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(outputLength)
        var t = byteArrayOf()
        var offset = 0
        var i: Byte = 1
        while (offset < outputLength) {
            mac.update(t)
            mac.update(info)
            mac.update(i)
            t = mac.doFinal()
            val copyLen = minOf(t.size, outputLength - offset)
            t.copyInto(result, offset, 0, copyLen)
            offset += copyLen
            i++
        }
        return result
    }
}
