package io.raccoonwallet.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.raccoonwallet.app.core.model.AppMode
import io.raccoonwallet.app.core.storage.IntegrityChecker
import io.raccoonwallet.app.feature.lock.PasswordLockScreen
import io.raccoonwallet.app.nav.ModeSelect
import io.raccoonwallet.app.nav.RaccoonWalletNavHost
import io.raccoonwallet.app.nav.SetupDkg
import io.raccoonwallet.app.nav.SignerIdle
import io.raccoonwallet.app.nav.VaultDashboard
import io.raccoonwallet.app.ui.theme.RaccoonWalletTheme
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as RaccoonWalletApp
        val passwordManager = app.masterPasswordManager
        val needsPassword = passwordManager.isPasswordConfigured()

        // If no password is configured, run integrity check immediately (original flow)
        val earlyStartDestination: Any? = if (!needsPassword) {
            runIntegrityAndResolveStart(app)
        } else {
            null
        }

        setContent {
            val isUnlocked by passwordManager.unlocked.collectAsState()

            if (needsPassword && !isUnlocked) {
                RaccoonWalletTheme {
                    PasswordLockScreen(onUnlock = { password ->
                        passwordManager.unlock(password)
                    })
                }
            } else {
                val startDestination = remember(isUnlocked) {
                    earlyStartDestination ?: runIntegrityAndResolveStart(app)
                }

                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val isSigner = remember(currentEntry) {
                    val route = currentEntry?.destination?.route ?: ""
                    route.contains("Signer") || route.contains("SetupDkg") &&
                        currentEntry?.arguments?.getBoolean("isVault") == false
                }

                RaccoonWalletTheme(isSigner = isSigner) {
                    RaccoonWalletNavHost(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    private fun runIntegrityAndResolveStart(app: RaccoonWalletApp): Any {
        val integrityResult = runBlocking { IntegrityChecker.check(app) }
        if (integrityResult is IntegrityChecker.Result.Corrupted) {
            runBlocking { IntegrityChecker.fullReset(app) }
            app.reinitPublicStore()
        }
        app.appSettings.refreshDiagnostics()

        return if (integrityResult is IntegrityChecker.Result.Corrupted) {
            ModeSelect(showResetNotice = true)
        } else {
            val snapshot = (integrityResult as IntegrityChecker.Result.OK).snapshot
            when {
                snapshot.appMode == null -> ModeSelect()
                !snapshot.dkgComplete -> SetupDkg(isVault = snapshot.appMode == AppMode.VAULT)
                snapshot.appMode == AppMode.VAULT -> VaultDashboard
                else -> SignerIdle
            }
        }
    }
}
