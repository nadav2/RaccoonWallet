package io.raccoonwallet.app.core.storage

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class PasswordCipherTest {

    // Use lightweight KDF params for fast tests while still exercising the full code path
    private val testMemory = 1024
    private val testIterations = 1
    private val testParallelism = 1

    private fun deriveKey(password: String, salt: ByteArray): ByteArray =
        PasswordCipher.deriveKey(password.toCharArray(), salt, testMemory, testIterations, testParallelism)

    @Test
    fun `deriveKey produces 32-byte key`() {
        val salt = PasswordCipher.generateSalt()
        val key = deriveKey("testpass", salt)
        assertEquals(32, key.size)
    }

    @Test
    fun `same password and salt produce same key`() {
        val salt = PasswordCipher.generateSalt()
        val key1 = deriveKey("hello", salt)
        val key2 = deriveKey("hello", salt)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun `different passwords produce different keys`() {
        val salt = PasswordCipher.generateSalt()
        val key1 = deriveKey("password1", salt)
        val key2 = deriveKey("password2", salt)
        assertFalse(key1.contentEquals(key2))
    }

    @Test
    fun `encrypt-decrypt roundtrip`() {
        val salt = PasswordCipher.generateSalt()
        val key = deriveKey("testpass", salt)
        val plaintext = "secret data".toByteArray()
        val encrypted = PasswordCipher.encrypt(key, salt, plaintext)
        val decrypted = PasswordCipher.decrypt(key, encrypted)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong password fails decryption`() {
        val salt = PasswordCipher.generateSalt()
        val rightKey = deriveKey("right", salt)
        val wrongKey = deriveKey("wrong", salt)
        val encrypted = PasswordCipher.encrypt(rightKey, salt, "data".toByteArray())
        PasswordCipher.decrypt(wrongKey, encrypted)
    }

    @Test
    fun `extractSalt returns first 32 bytes`() {
        val salt = PasswordCipher.generateSalt()
        val key = deriveKey("test", salt)
        val encrypted = PasswordCipher.encrypt(key, salt, "data".toByteArray())
        val extracted = PasswordCipher.extractSalt(encrypted)
        assertArrayEquals(salt, extracted)
    }

    @Test
    fun `generateSalt returns 32 bytes`() {
        val salt = PasswordCipher.generateSalt()
        assertEquals(32, salt.size)
    }

    @Test
    fun `two salts are different`() {
        val s1 = PasswordCipher.generateSalt()
        val s2 = PasswordCipher.generateSalt()
        assertFalse(s1.contentEquals(s2))
    }
}
