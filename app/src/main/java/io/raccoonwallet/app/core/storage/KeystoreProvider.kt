package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.core.model.AuthMode

/**
 * Provides KeystoreAead instances for encrypting store files.
 *
 * - Public store: no authentication requirement
 * - Secret store: authentication based on user's AuthMode choice during setup
 */
object KeystoreProvider {

    private const val PUBLIC_KEY_ALIAS = "raccoonwallet_public_master"
    private const val SECRET_KEY_ALIAS = "raccoonwallet_secret_master"

    fun publicAead(): KeystoreAead {
        KeystoreCipher.ensureKey(PUBLIC_KEY_ALIAS, authMode = AuthMode.NONE)
        return KeystoreAead(PUBLIC_KEY_ALIAS)
    }

    fun secretAead(authMode: AuthMode): KeystoreAead {
        KeystoreCipher.ensureKey(SECRET_KEY_ALIAS, authMode = authMode)
        return KeystoreAead(SECRET_KEY_ALIAS)
    }
}
