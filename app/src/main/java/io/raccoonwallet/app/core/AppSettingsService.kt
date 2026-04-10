package io.raccoonwallet.app.core

import android.content.Context
import android.nfc.NfcAdapter
import io.raccoonwallet.app.core.model.DiagnosticsData
import io.raccoonwallet.app.core.storage.KeystoreCipher
import io.raccoonwallet.app.core.storage.PublicStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AppSettingsService(
    private val context: Context,
    private val publicStore: PublicStore,
    private val publicStoreFile: File,
    private val secretStoreFile: File,
    private val versionName: String,
    private val resetAction: suspend () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _diagnostics = MutableStateFlow<DiagnosticsData?>(null)
    val diagnostics: StateFlow<DiagnosticsData?> = _diagnostics.asStateFlow()

    fun refreshDiagnostics() {
        scope.launch {
            val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            val pubExists = publicStoreFile.exists()
            val secExists = secretStoreFile.exists()
            _diagnostics.value = DiagnosticsData(
                versionName = versionName,
                mode = publicStore.getAppMode()?.name ?: "Not set",
                accountCount = publicStore.getAccounts().size,
                dkgComplete = publicStore.isDkgComplete(),
                publicStoreExists = pubExists,
                publicStoreSize = if (pubExists) publicStoreFile.length() else 0L,
                secretStoreExists = secExists,
                secretStoreSize = if (secExists) secretStoreFile.length() else 0L,
                publicKeyOk = KeystoreCipher.hasUsableAesKey("raccoonwallet_public_master"),
                secretKeyOk = KeystoreCipher.hasUsableAesKey("raccoonwallet_secret_master"),
                authModeDisplay = publicStore.getAuthMode().displayName,
                strongBox = KeystoreCipher.isStrongBoxBacked("raccoonwallet_secret_master"),
                nfcAvailable = nfcAdapter != null
            )
        }
    }

    fun resetMode() {
        scope.launch { resetAction() }
    }
}
