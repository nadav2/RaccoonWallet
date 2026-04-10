package io.raccoonwallet.app.core.storage

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.raccoonwallet.app.core.model.AuthMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Triggers biometric authentication so the Android Keystore unlocks
 * the secret store's master key.
 */
object BiometricGate {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    private const val BIOMETRIC_ONLY = BiometricManager.Authenticators.BIOMETRIC_STRONG

    suspend fun authenticate(
        activity: FragmentActivity,
        authMode: AuthMode = AuthMode.BIOMETRIC_OR_DEVICE
    ): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
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

            prompt.authenticate(info)

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
