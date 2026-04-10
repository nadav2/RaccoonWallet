package io.raccoonwallet.app.core.model

import android.security.keystore.KeyProperties

enum class AuthMode(
    val displayName: String,
    val authenticators: Int
) {
    NONE("None", 0),
    BIOMETRIC_OR_DEVICE(
        "Biometric + PIN",
        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
    ),
    BIOMETRIC_ONLY(
        "Biometric only",
        KeyProperties.AUTH_BIOMETRIC_STRONG
    )
}
