package io.raccoonwallet.app.core.storage

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.raccoonwallet.app.core.model.AuthMode
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

sealed class AuthResult {
    data class Success(val cipher: Cipher?) : AuthResult()
    data object Denied : AuthResult()
}

/**
 * Triggers biometric authentication so the Android Keystore unlocks
 * the secret store's master key.
 *
 * For BIOMETRIC_ONLY: uses CryptoObject for per-operation auth (no time window).
 * For BIOMETRIC_OR_DEVICE: time-window auth (CryptoObject not supported with DEVICE_CREDENTIAL).
 */
object BiometricGate {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private const val BIOMETRIC_ONLY = BiometricManager.Authenticators.BIOMETRIC_STRONG

    suspend fun authenticate(
        activity: FragmentActivity,
        authMode: AuthMode = AuthMode.BIOMETRIC_OR_DEVICE,
        cipher: Cipher? = null
    ): AuthResult =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) {
                        val authedCipher = result.cryptoObject?.cipher ?: cipher
                        cont.resume(AuthResult.Success(authedCipher))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(AuthResult.Denied)
                }

                override fun onAuthenticationFailed() {
                    // Individual attempt failed — prompt stays open
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)

            val biometricOnly = authMode == AuthMode.BIOMETRIC_ONLY
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Raccoon Wallet")
                .setSubtitle(
                    if (biometricOnly) "Biometric required to access key shares"
                    else "Authenticate to access key shares"
                )
                .apply {
                    if (biometricOnly) {
                        setAllowedAuthenticators(BIOMETRIC_ONLY)
                        setNegativeButtonText("Cancel")
                    } else {
                        setAllowedAuthenticators(AUTHENTICATORS)
                    }
                }
                .build()

            if (cipher != null && biometricOnly) {
                prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            } else {
                prompt.authenticate(info)
            }

            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        return bm.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasBiometric(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        return bm.canAuthenticate(BIOMETRIC_ONLY) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
