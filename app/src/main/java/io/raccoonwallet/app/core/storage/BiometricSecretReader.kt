package io.raccoonwallet.app.core.storage

import androidx.fragment.app.FragmentActivity
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.deps

/**
 * Shared helper for authenticating and reading from the biometric-gated secret store.
 * Used by both VaultSignViewModel and SignerConfirmViewModel to avoid duplicating
 * the CryptoObject / time-window / no-auth dispatch.
 */
object BiometricSecretReader {

    suspend fun authenticateAndRead(
        authMode: AuthMode,
        activity: FragmentActivity?,
        secretStore: SecretStore,
        aead: KeystoreAead?
    ): SecretStoreData? {
        // Hold the password key alive during biometric/NFC operations so
        // ProcessLifecycleOwner ON_STOP doesn't wipe it mid-signing.
        val passwordManager = activity?.application?.deps?.masterPasswordManager
        passwordManager?.acquireHold()
        try {
            return doAuthenticateAndRead(authMode, activity, secretStore, aead)
        } finally {
            passwordManager?.releaseHold()
        }
    }

    private suspend fun doAuthenticateAndRead(
        authMode: AuthMode,
        activity: FragmentActivity?,
        secretStore: SecretStore,
        aead: KeystoreAead?
    ): SecretStoreData? {
        return when (authMode) {
            AuthMode.NONE -> secretStore.readData()
            AuthMode.BIOMETRIC_ONLY -> {
                val act = requireActivity(activity)
                val resolvedAead = aead ?: throw RuntimeException("KeystoreAead required for BIOMETRIC_ONLY")
                val rawBytes = secretStore.readEncryptedBytes()
                    ?: return secretStore.readData()
                val nonce = KeystoreCipher.extractNonce(rawBytes)
                val cipher = resolvedAead.createDecryptCipher(nonce)
                when (val result = BiometricGate.authenticate(act, authMode, cipher)) {
                    is AuthResult.Success -> secretStore.readData(result.cipher, rawBytes)
                    is AuthResult.Denied -> null
                }
            }
            AuthMode.BIOMETRIC_OR_DEVICE -> {
                val act = requireActivity(activity)
                when (BiometricGate.authenticate(act, authMode)) {
                    is AuthResult.Success -> secretStore.readData()
                    is AuthResult.Denied -> null
                }
            }
        }
    }

    /**
     * Authenticate via biometric and rewrite the secret store (read from cache, write back).
     * Used when changing/setting/removing the master password with biometric auth modes.
     * Returns true on success, false if biometric was denied.
     */
    suspend fun authenticateAndRewrite(
        authMode: AuthMode,
        activity: FragmentActivity?,
        secretStore: SecretStore,
        aead: KeystoreAead?
    ): Boolean {
        return when (authMode) {
            AuthMode.NONE -> {
                secretStore.rewrite()
                true
            }
            AuthMode.BIOMETRIC_ONLY -> {
                val act = requireActivity(activity)
                val resolvedAead = aead ?: throw RuntimeException("KeystoreAead required for BIOMETRIC_ONLY")
                val cipher = resolvedAead.createEncryptCipher()
                when (val result = BiometricGate.authenticate(act, authMode, cipher)) {
                    is AuthResult.Success -> {
                        val authedCipher = result.cipher ?: throw RuntimeException("No cipher from biometric")
                        secretStore.rewriteWithCipher(authedCipher)
                        true
                    }
                    is AuthResult.Denied -> false
                }
            }
            AuthMode.BIOMETRIC_OR_DEVICE -> {
                val act = requireActivity(activity)
                when (BiometricGate.authenticate(act, authMode)) {
                    is AuthResult.Success -> {
                        secretStore.rewrite()
                        true
                    }
                    is AuthResult.Denied -> false
                }
            }
        }
    }

    private fun requireActivity(activity: FragmentActivity?): FragmentActivity =
        activity ?: throw RuntimeException("Activity not available for biometric prompt")
}
