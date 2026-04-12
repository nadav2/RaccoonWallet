package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.core.crypto.ConstantTime
import java.io.File
import javax.crypto.AEADBadTagException

/**
 * Manages two security features:
 *
 * 1. **Auto-wipe**: optional toggle that wipes all data after [MAX_ATTEMPTS]
 *    consecutive wrong password attempts. Counter persists on disk.
 * 2. **Duress code**: a separate code that silently wipes all data when
 *    entered on the lock screen, leaving a clean-slate app.
 *
 * Config is stored in a plain text file (must be readable before password
 * unlock). The duress code uses the same PasswordCipher verifier pattern
 * as MasterPasswordManager.
 */
class WipeProtection(
    private val configFile: File,
    private val duressVerifierFile: File
) {
    companion object {
        const val MAX_ATTEMPTS = 10
        const val MIN_DURESS_LENGTH = 4
        private val DURESS_PLAINTEXT = "raccoonwallet-duress-ok".toByteArray()
    }

    // ── Auto-wipe config ──

    fun isAutoWipeEnabled(): Boolean = readConfig().first

    fun setAutoWipeEnabled(enabled: Boolean) {
        val (_, attempts) = readConfig()
        writeConfig(enabled, attempts)
    }

    data class FailedAttemptResult(val shouldWipe: Boolean, val attemptsRemaining: Int?)

    /**
     * Record a failed attempt and return whether to wipe + remaining attempts.
     * Single file read + write per call.
     */
    fun recordFailedAttempt(): FailedAttemptResult {
        val (enabled, attempts) = readConfig()
        val newCount = attempts + 1
        writeConfig(enabled, newCount)
        val wipe = enabled && newCount >= MAX_ATTEMPTS
        val remaining = if (enabled) (MAX_ATTEMPTS - newCount).coerceAtLeast(0) else null
        return FailedAttemptResult(wipe, remaining)
    }

    fun resetFailedAttempts() {
        val (enabled, _) = readConfig()
        writeConfig(enabled, 0)
    }

    // ── Duress code ──

    fun isDuressCodeConfigured(): Boolean = duressVerifierFile.exists()

    fun setupDuressCode(code: CharArray) {
        try {
            val salt = PasswordCipher.generateSalt()
            val key = PasswordCipher.deriveKey(code, salt)
            val verifierBytes = PasswordCipher.encrypt(key, salt, DURESS_PLAINTEXT)
            duressVerifierFile.writeBytes(verifierBytes)
            ConstantTime.wipe(key)
        } finally {
            code.fill('\u0000')
        }
    }

    fun checkDuressCode(input: CharArray): Boolean {
        if (!duressVerifierFile.exists()) { input.fill('\u0000'); return false }
        val data = duressVerifierFile.readBytes()
        val salt = PasswordCipher.extractSalt(data)
        val key = PasswordCipher.deriveKey(input, salt)
        input.fill('\u0000')
        return try {
            val decrypted = PasswordCipher.decrypt(key, data)
            ConstantTime.equals(decrypted, DURESS_PLAINTEXT)
        } catch (_: AEADBadTagException) {
            false
        } finally {
            ConstantTime.wipe(key)
        }
    }

    // ── Reset ──

    fun deleteAll() {
        if (configFile.exists()) configFile.delete()
        if (duressVerifierFile.exists()) duressVerifierFile.delete()
    }

    // ── Config file I/O ──

    private fun readConfig(): Pair<Boolean, Int> {
        if (!configFile.exists()) return Pair(false, 0)
        return try {
            val lines = configFile.readLines()
            val enabled = lines.getOrNull(0)?.toBooleanStrictOrNull() ?: false
            val attempts = lines.getOrNull(1)?.toIntOrNull() ?: 0
            Pair(enabled, attempts)
        } catch (_: Exception) {
            Pair(false, 0)
        }
    }

    private fun writeConfig(enabled: Boolean, attempts: Int) {
        configFile.writeText("$enabled\n$attempts\n")
    }
}
