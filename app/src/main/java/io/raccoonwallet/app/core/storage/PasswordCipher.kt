package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.core.crypto.ConstantTime
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based AES-256-GCM encryption for the outer layer of secret_store.enc.
 *
 * On-disk format (when password enabled):
 *   [32-byte salt] [12-byte GCM nonce] [AES-256-GCM ciphertext+tag]
 */
object PasswordCipher {

    private const val SALT_LENGTH = 32
    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_LENGTH = 32
    const val HEADER_LENGTH = SALT_LENGTH + GCM_NONCE_LENGTH

    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        memory: Int = 65536,
        iterations: Int = 3,
        parallelism: Int = 4
    ): ByteArray {
        val passwordBytes = charArrayToUtf8(password)
        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            val key = ByteArray(KEY_LENGTH)
            generator.generateBytes(passwordBytes, key)
            return key
        } finally {
            ConstantTime.wipe(passwordBytes)
        }
    }

    fun encrypt(key: ByteArray, salt: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == KEY_LENGTH) { "Key must be $KEY_LENGTH bytes" }
        require(salt.size == SALT_LENGTH) { "Salt must be $SALT_LENGTH bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return salt + nonce + ciphertext
    }

    fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(data.size > HEADER_LENGTH) { "Data too short for password-encrypted format" }
        require(key.size == KEY_LENGTH) { "Key must be $KEY_LENGTH bytes" }
        val nonce = data.copyOfRange(SALT_LENGTH, HEADER_LENGTH)
        val ciphertext = data.copyOfRange(HEADER_LENGTH, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    fun extractSalt(data: ByteArray): ByteArray {
        require(data.size > HEADER_LENGTH) { "Data too short" }
        return data.copyOfRange(0, SALT_LENGTH)
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun charArrayToUtf8(chars: CharArray): ByteArray {
        val buf = java.nio.CharBuffer.wrap(chars)
        val encoded = Charsets.UTF_8.encode(buf)
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        if (encoded.hasArray()) encoded.array().fill(0)
        return bytes
    }
}
