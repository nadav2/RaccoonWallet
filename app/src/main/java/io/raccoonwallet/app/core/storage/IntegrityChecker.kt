package io.raccoonwallet.app.core.storage

import io.raccoonwallet.app.RaccoonWalletApp
import io.raccoonwallet.app.core.model.Account
import io.raccoonwallet.app.core.model.AppMode
import io.raccoonwallet.app.core.model.AuthMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore

/**
 * Checks wallet data integrity at startup without requiring decryption
 * or biometric authentication. Detects:
 * - Corrupted public store
 * - Missing accounts after completed DKG
 * - Deleted or empty secret store file
 * - Invalidated Keystore encryption key (e.g., biometric enrollment change)
 */
object IntegrityChecker {

    private const val PUBLIC_KEY_ALIAS = "raccoonwallet_public_master"
    private const val SECRET_KEY_ALIAS = "raccoonwallet_secret_master"

    data class Snapshot(
        val appMode: AppMode?,
        val dkgComplete: Boolean,
        val accounts: List<Account>,
        val authMode: AuthMode
    )

    sealed class Result {
        data class OK(val snapshot: Snapshot) : Result()
        data class Corrupted(val reason: String) : Result()
    }

    suspend fun check(app: RaccoonWalletApp): Result {
        val publicResult = checkPublicStore(app.publicStore)
        if (publicResult is Result.Corrupted) return publicResult

        val snapshot = (publicResult as Result.OK).snapshot

        // Detect partial DKG: accounts written but dkgComplete never set (crash mid-setup)
        if (!snapshot.dkgComplete && snapshot.accounts.isNotEmpty()) {
            return Result.Corrupted("Setup was interrupted after key generation. Please start over.")
        }

        // DKG not complete → nothing secret to validate
        if (!snapshot.dkgComplete) return Result.OK(snapshot)

        if (snapshot.accounts.isEmpty()) {
            return Result.Corrupted("Account data missing — setup may not have completed")
        }

        val secretFile = app.secretStoreFile
        if (!secretFile.exists() || secretFile.length() == 0L) {
            return Result.Corrupted("Key share data is missing")
        }

        if (!KeystoreCipher.hasUsableAesKey(PUBLIC_KEY_ALIAS)) {
            return Result.Corrupted("Wallet data encryption key is missing or invalid")
        }
        if (!KeystoreCipher.hasUsableAesKey(SECRET_KEY_ALIAS)) {
            return Result.Corrupted(
                "Key share encryption key is missing or invalid" +
                    when (snapshot.authMode) {
                        AuthMode.BIOMETRIC_ONLY -> " — biometric enrollment changed"
                        AuthMode.BIOMETRIC_OR_DEVICE -> " — biometric enrollment may have changed"
                        AuthMode.NONE -> ""
                    }
            )
        }

        // For non-biometric wallets, also verify the secret file actually decrypts.
        // Biometric-gated keys are already validated above (hasUsableAesKey detects
        // KeyPermanentlyInvalidatedException without requiring user auth).
        if (snapshot.authMode == AuthMode.NONE) {
            try {
                withContext(Dispatchers.IO) {
                    KeystoreCipher.decrypt(SECRET_KEY_ALIAS, secretFile.readBytes())
                }
            } catch (_: Exception) {
                return Result.Corrupted("Key share data is unreadable")
            }
        }

        return Result.OK(snapshot)
    }

    /**
     * Wipe all wallet data and encryption keys for a clean restart.
     */
    suspend fun fullReset(app: RaccoonWalletApp) {
        app.publicStore.deleteAll()
        app.secretStoreFile.delete()
        java.io.File(app.secretStoreFile.parent, "${app.secretStoreFile.name}.tmp").delete()
        java.io.File(app.publicStoreFile.parent, "${app.publicStoreFile.name}.tmp").delete()
        app.masterPasswordManager.deletePassword()
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            withContext(Dispatchers.IO) {
                ks.load(null)
                ks.deleteEntry(PUBLIC_KEY_ALIAS)
                ks.deleteEntry(SECRET_KEY_ALIAS)
            }
        } catch (_: Exception) {
            // Best effort — key may not exist
        }
    }

    private suspend fun checkPublicStore(publicStore: PublicStore): Result {
        val snapshot = try {
            Snapshot(
                appMode = publicStore.getAppMode(),
                dkgComplete = publicStore.isDkgComplete(),
                accounts = publicStore.getAccounts(),
                authMode = publicStore.getAuthMode()
            )
        } catch (_: Exception) {
            return Result.Corrupted("Wallet data is unreadable")
        }
        return Result.OK(snapshot)
    }
}
