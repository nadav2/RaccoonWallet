package io.raccoonwallet.app.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import io.raccoonwallet.app.core.model.AuthMode
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Direct Android Keystore AES-256-GCM encryption.
 * Supports biometric-gated keys (setUserAuthenticationRequired).
 *
 * Unlike Tink, this does NOT do a validation encrypt during key creation,
 * so it works correctly with biometric-gated Keystore keys.
 */
object KeystoreCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_LENGTH = 12
    private const val AUTH_VALIDITY_TIME_WINDOW = 5

    fun ensureKey(alias: String, authMode: AuthMode) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (ks.containsAlias(alias)) {
            val existing = try {
                ks.getKey(alias, null)
            } catch (_: Exception) {
                null
            }
            if (existing is SecretKey && existing.algorithm == KeyProperties.KEY_ALGORITHM_AES) {
                return
            }
            ks.deleteEntry(alias)
        }

        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        applyAuthMode(spec, authMode)

        generateWithStrongBoxFallback(spec)
    }

    /**
     * Try StrongBox first; fall back to TEE.
     * Catches ProviderException (parent of StrongBoxUnavailableException) because
     * some devices throw the parent class instead of the specific subclass.
     */
    private fun generateWithStrongBoxFallback(spec: KeyGenParameterSpec.Builder) {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        try {
            keyGenerator.init(spec.setIsStrongBoxBacked(true).build())
            keyGenerator.generateKey()
        } catch (_: java.security.ProviderException) {
            keyGenerator.init(spec.setIsStrongBoxBacked(false).build())
            keyGenerator.generateKey()
        }
    }

    private fun applyAuthMode(spec: KeyGenParameterSpec.Builder, authMode: AuthMode) {
        if (authMode == AuthMode.NONE) {
            spec.setUnlockedDeviceRequired(true)
            return
        }
        // BIOMETRIC_ONLY: timeout=0 → per-operation auth via CryptoObject (no time window)
        // BIOMETRIC_OR_DEVICE: timeout=5s → DEVICE_CREDENTIAL doesn't support CryptoObject
        val timeout = if (authMode == AuthMode.BIOMETRIC_ONLY) 0 else AUTH_VALIDITY_TIME_WINDOW
        spec.setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(timeout, authMode.authenticators)
        if (authMode == AuthMode.BIOMETRIC_ONLY) {
            spec.setInvalidatedByBiometricEnrollment(true)
        }
    }

    /**
     * Check if the key exists, is AES, and hasn't been permanently invalidated
     * (e.g., by a biometric enrollment change).
     *
     * For biometric-gated keys, Cipher.init() distinguishes:
     * - [UserNotAuthenticatedException] → key is valid, user just needs to auth → true
     * - [KeyPermanentlyInvalidatedException] → key is dead → false
     */
    fun hasUsableAesKey(alias: String): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        if (!ks.containsAlias(alias)) return false
        val key = try {
            ks.getKey(alias, null)
        } catch (_: Exception) {
            return false
        }
        if (key !is SecretKey || key.algorithm != KeyProperties.KEY_ALGORITHM_AES) return false

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            true
        } catch (_: KeyPermanentlyInvalidatedException) {
            false
        } catch (_: UserNotAuthenticatedException) {
            true // Key is valid, just locked behind auth
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Encrypt with AES-256-GCM. Returns nonce(12) || ciphertext+tag.
     * Throws UserNotAuthenticatedException if key requires biometric and user hasn't authed.
     */
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val key = loadKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(alias.toByteArray())
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    /**
     * Decrypt AES-256-GCM. Input: nonce(12) || ciphertext+tag.
     * Throws UserNotAuthenticatedException if key requires biometric and user hasn't authed.
     */
    fun decrypt(alias: String, data: ByteArray): ByteArray {
        require(data.size > GCM_NONCE_LENGTH) { "Data too short" }
        val nonce = data.copyOfRange(0, GCM_NONCE_LENGTH)
        val ciphertext = data.copyOfRange(GCM_NONCE_LENGTH, data.size)
        val key = loadKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(alias.toByteArray())
        return cipher.doFinal(ciphertext)
    }

    // ── CryptoObject support (per-operation auth with BIOMETRIC_ONLY) ──

    fun extractNonce(data: ByteArray): ByteArray {
        require(data.size > GCM_NONCE_LENGTH) { "Data too short" }
        return data.copyOfRange(0, GCM_NONCE_LENGTH)
    }

    fun createEncryptCipher(alias: String): Cipher {
        val key = loadKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun createDecryptCipher(alias: String, nonce: ByteArray): Cipher {
        val key = loadKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher
    }

    fun encryptWithCipher(alias: String, cipher: Cipher, plaintext: ByteArray): ByteArray {
        cipher.updateAAD(alias.toByteArray())
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    fun decryptWithCipher(alias: String, cipher: Cipher, data: ByteArray): ByteArray {
        require(data.size > GCM_NONCE_LENGTH) { "Data too short" }
        val ciphertext = data.copyOfRange(GCM_NONCE_LENGTH, data.size)
        cipher.updateAAD(alias.toByteArray())
        return cipher.doFinal(ciphertext)
    }

    private fun loadKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        val key = ks.getKey(alias, null)
            ?: throw java.security.KeyStoreException("Key alias '$alias' not found in Keystore")
        return key as? SecretKey
            ?: throw java.security.KeyStoreException("Key alias '$alias' is not an AES SecretKey")
    }

    fun deleteKey(alias: String) {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        ks.deleteEntry(alias)
    }

    fun isStrongBoxBacked(alias: String): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        val key = try {
            ks.getKey(alias, null) as? SecretKey ?: return false
        } catch (_: Exception) {
            return false
        }
        return try {
            val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
            info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } catch (_: Exception) {
            false
        }
    }
}
