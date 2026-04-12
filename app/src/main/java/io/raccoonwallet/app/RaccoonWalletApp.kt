package io.raccoonwallet.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import io.raccoonwallet.app.core.AppSettingsService
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.network.ChainManager
import io.raccoonwallet.app.core.storage.EncryptedJsonStore
import io.raccoonwallet.app.core.storage.KeystoreAead
import io.raccoonwallet.app.core.storage.KeystoreCipher
import io.raccoonwallet.app.core.storage.PublicStore
import io.raccoonwallet.app.core.storage.PublicStoreData
import io.raccoonwallet.app.core.storage.SecretStore
import io.raccoonwallet.app.core.storage.SecretStoreData
import io.raccoonwallet.app.core.storage.KeystoreProvider
import io.raccoonwallet.app.core.storage.MasterPasswordManager
import io.raccoonwallet.app.core.transport.MessageCodec
import io.raccoonwallet.app.core.transport.TransportBridge
import io.raccoonwallet.app.core.transport.nfc.ApduChunker
import io.raccoonwallet.app.core.transport.nfc.HceSessionManager
import io.raccoonwallet.app.core.transport.nfc.NfcReaderTransport
import io.raccoonwallet.app.core.transport.qr.QrTransport
import java.io.File

class RaccoonWalletApp : Application() {
    lateinit var publicStore: PublicStore
    lateinit var chainManager: ChainManager
    lateinit var apduChunker: ApduChunker
    lateinit var messageCodec: MessageCodec
    lateinit var nfcReaderTransport: NfcReaderTransport
    lateinit var transportBridge: TransportBridge
    lateinit var hceSessionManager: HceSessionManager
    lateinit var versionName: String
    lateinit var appSettings: AppSettingsService
    lateinit var masterPasswordManager: MasterPasswordManager

    val publicStoreFile: File
        get() = File(filesDir, "public_store.enc")

    val secretStoreFile: File
        get() = File(filesDir, "secret_store.enc")

    val passwordVerifierFile: File
        get() = File(filesDir, "password_verifier.enc")

    private val passwordKeyProvider: () -> ByteArray? = { masterPasswordManager.getKey() }

    fun getSecretAead(authMode: AuthMode): KeystoreAead =
        KeystoreProvider.secretAead(authMode)

    fun getSecretStore(authMode: AuthMode): SecretStore {
        return SecretStore(
            EncryptedJsonStore(
                file = secretStoreFile,
                aead = KeystoreProvider.secretAead(authMode),
                serializer = SecretStoreData.serializer(),
                defaultValue = SecretStoreData(),
                passwordKeyProvider = passwordKeyProvider
            )
        )
    }

    suspend fun resetAction() {
        val authMode = publicStore.getAuthMode()
        getSecretStore(authMode).deleteAll()
        try {
            KeystoreCipher.deleteKey("raccoonwallet_secret_master")
        } catch (_: Exception) { }
        publicStore.deleteAll()
        masterPasswordManager.deletePassword()
    }

    /**
     * Re-create the public store after a full reset that deleted Keystore keys.
     * Without this, the existing [publicStore] holds a stale [KeystoreAead] whose
     * underlying AES key no longer exists, causing a crash on the next write.
     */
    fun reinitPublicStore() {
        publicStore = PublicStore(
            EncryptedJsonStore(
                file = publicStoreFile,
                aead = KeystoreProvider.publicAead(),
                serializer = PublicStoreData.serializer(),
                defaultValue = PublicStoreData(),
                passwordKeyProvider = passwordKeyProvider
            )
        )
    }

    override fun onCreate() {
        super.onCreate()

        versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
        masterPasswordManager = MasterPasswordManager(passwordVerifierFile)

        publicStore = PublicStore(
            EncryptedJsonStore(
                file = publicStoreFile,
                aead = KeystoreProvider.publicAead(),
                serializer = PublicStoreData.serializer(),
                defaultValue = PublicStoreData(),
                passwordKeyProvider = passwordKeyProvider
            )
        )

        chainManager = ChainManager()
        apduChunker = ApduChunker()
        messageCodec = MessageCodec()
        hceSessionManager = HceSessionManager()
        nfcReaderTransport = NfcReaderTransport(apduChunker, messageCodec)
        transportBridge = TransportBridge(nfcReaderTransport, QrTransport(messageCodec))

        appSettings = AppSettingsService(
            context = this,
            publicStore = publicStore,
            publicStoreFile = publicStoreFile,
            secretStoreFile = secretStoreFile,
            versionName = versionName,
            resetAction = ::resetAction
        )

        // Lock password when the app truly goes to background (process-level).
        // Activity-level onStop fires during biometric prompts and system overlays,
        // but ProcessLifecycleOwner only fires when the whole app is backgrounded.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) masterPasswordManager.lock()
            }
        )
    }
}

val Application.deps: RaccoonWalletApp get() = this as RaccoonWalletApp
