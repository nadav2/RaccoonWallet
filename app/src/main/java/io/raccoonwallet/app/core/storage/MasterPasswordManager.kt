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
    private var pendingSalt: ByteArray? = null
    private var holdCount = 0
    private var lockPending = false

    fun isPasswordConfigured(): Boolean = verifierFile.exists()

    fun getKey(): ByteArray? = synchronized(keyLock) {
        cachedKey?.copyOf()
    }

    /**
     * Full setup: derive key, write verifier, cache key. Use from settings
     * when DKG is already complete.
     */
    fun setupPassword(password: CharArray) {
        preparePassword(password)
        commitPassword()
    }

    /**
     * Phase 1 (DKG): derive and cache the key so stores can encrypt with it,
     * but do NOT write the verifier file yet. Call [commitPassword] after
     * DKG completes successfully.
     */
    fun preparePassword(password: CharArray) {
        try {
            val salt = PasswordCipher.generateSalt()
            val key = PasswordCipher.deriveKey(password, salt)
            synchronized(keyLock) {
                pendingSalt = salt
                cachedKey = key
            }
            _unlocked.value = true
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Phase 2 (DKG): write the verifier file, making the password permanent.
     * Only call after stores have been written successfully.
     */
    fun commitPassword() = synchronized(keyLock) {
        val key = cachedKey ?: return@synchronized
        val salt = pendingSalt ?: PasswordCipher.generateSalt()
        val verifierBytes = PasswordCipher.encrypt(key, salt, VERIFIER_PLAINTEXT)
        verifierFile.writeBytes(verifierBytes)
        pendingSalt = null
    }

    /**
     * Discard a prepared-but-uncommitted password. Called on DKG cancel.
     */
    fun discardPreparedPassword() = synchronized(keyLock) {
        if (!verifierFile.exists()) {
            // Only wipe if never committed
            cachedKey?.let { ConstantTime.wipe(it) }
            cachedKey = null
            pendingSalt = null
            _unlocked.value = false
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

    /** Must be called while holding [keyLock]. */
    private fun wipeKeyLocked() {
        cachedKey?.let { ConstantTime.wipe(it) }
        cachedKey = null
    }

    fun lock(): Unit = synchronized(keyLock) {
        if (holdCount > 0) {
            lockPending = true
            return
        }
        wipeKeyLocked()
        lockPending = false
        _unlocked.value = false
    }

    /**
     * Prevent [lock] from wiping the key while a critical operation
     * (signing, biometric prompt) is in progress. Must be paired with [releaseHold].
     */
    fun acquireHold(): Unit = synchronized(keyLock) {
        holdCount++
    }

    fun releaseHold(): Unit = synchronized(keyLock) {
        holdCount = (holdCount - 1).coerceAtLeast(0)
        if (holdCount == 0 && lockPending) {
            wipeKeyLocked()
            lockPending = false
            _unlocked.value = false
        }
    }

    /**
     * Verify that [password] matches the current master password.
     * Runs Argon2id derivation — call from a background thread.
     */
    fun verifyPassword(password: CharArray): Boolean {
        if (!verifierFile.exists()) { password.fill('\u0000'); return false }
        val data = verifierFile.readBytes()
        val salt = PasswordCipher.extractSalt(data)
        val key = PasswordCipher.deriveKey(password, salt)
        password.fill('\u0000')
        return try {
            val decrypted = PasswordCipher.decrypt(key, data)
            ConstantTime.equals(decrypted, VERIFIER_PLAINTEXT)
        } catch (_: AEADBadTagException) {
            false
        } finally {
            ConstantTime.wipe(key)
        }
    }

    /**
     * Change the master password. Uses the already-cached key internally.
     * Caller must verify the old password first via [verifyPassword].
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

    /**
     * Clear the derived key without emitting state changes.
     * Use before rewriting stores to strip the password layer,
     * then call [deletePassword] to remove the verifier and emit.
     */
    fun clearKey(): Unit = synchronized(keyLock) {
        if (holdCount > 0) {
            lockPending = true
            return
        }
        wipeKeyLocked()
    }

    fun deletePassword() {
        if (verifierFile.exists()) verifierFile.delete()
        synchronized(keyLock) {
            wipeKeyLocked()
            lockPending = false
        }
        _unlocked.value = false
    }
}
