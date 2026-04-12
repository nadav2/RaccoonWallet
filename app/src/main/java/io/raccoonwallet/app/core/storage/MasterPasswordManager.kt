package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.core.crypto.ConstantTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.crypto.AEADBadTagException

/**
 * Manages master password setup, verification, and derived-key lifecycle.
 *
 * The password is verified against a small verifier file (`password_verifier.enc`)
 * encrypted with PasswordCipher. The derived key is cached in memory while the
 * app is unlocked and wiped on lock (every background transition).
 *
 * The verifier file's existence is the source of truth for "is password configured".
 */
class MasterPasswordManager(private val verifierFile: File) {

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        private val VERIFIER_PLAINTEXT = "raccoonwallet-password-ok".toByteArray()
    }

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val keyLock = Any()
    private var cachedKey: ByteArray? = null

    fun isPasswordConfigured(): Boolean = verifierFile.exists()

    fun getKey(): ByteArray? = synchronized(keyLock) {
        cachedKey?.copyOf()
    }

    fun setupPassword(password: CharArray) {
        try {
            val salt = PasswordCipher.generateSalt()
            val key = PasswordCipher.deriveKey(password, salt)
            val verifierBytes = PasswordCipher.encrypt(key, salt, VERIFIER_PLAINTEXT)
            verifierFile.writeBytes(verifierBytes)
            synchronized(keyLock) { cachedKey = key }
            _unlocked.value = true
        } finally {
            password.fill('\u0000')
        }
    }

    fun unlock(password: CharArray): Boolean {
        if (!verifierFile.exists()) { password.fill('\u0000'); return false }
        val data = verifierFile.readBytes()
        val salt = PasswordCipher.extractSalt(data)
        val key = PasswordCipher.deriveKey(password, salt)
        password.fill('\u0000')
        return try {
            val decrypted = PasswordCipher.decrypt(key, data)
            if (ConstantTime.equals(decrypted, VERIFIER_PLAINTEXT)) {
                synchronized(keyLock) { cachedKey = key }
                _unlocked.value = true
                true
            } else {
                ConstantTime.wipe(key)
                false
            }
        } catch (_: AEADBadTagException) {
            ConstantTime.wipe(key)
            false
        }
    }

    fun lock() = synchronized(keyLock) {
        cachedKey?.let { ConstantTime.wipe(it) }
        cachedKey = null
        _unlocked.value = false
    }

    /**
     * Change the master password. Uses the already-cached key to verify the old password
     * (avoids a redundant Argon2id derivation). Derives the new key, swaps the cached key,
     * then writes the new verifier.
     */
    fun changePassword(newPassword: CharArray): Boolean = synchronized(keyLock) {
        val existing = cachedKey ?: return false
        if (!verifierFile.exists()) return false
        val data = verifierFile.readBytes()
        try {
            val decrypted = PasswordCipher.decrypt(existing, data)
            if (!ConstantTime.equals(decrypted, VERIFIER_PLAINTEXT)) return false
        } catch (_: AEADBadTagException) {
            return false
        }

        val newSalt = PasswordCipher.generateSalt()
        val newKey = PasswordCipher.deriveKey(newPassword, newSalt)

        ConstantTime.wipe(existing)
        cachedKey = newKey

        val newVerifier = PasswordCipher.encrypt(newKey, newSalt, VERIFIER_PLAINTEXT)
        verifierFile.writeBytes(newVerifier)

        _unlocked.value = true
        true
    }

    fun deletePassword() {
        if (verifierFile.exists()) verifierFile.delete()
        lock()
    }
}
