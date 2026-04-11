package io.raccoonwallet.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.raccoonwallet.app.core.model.AppMode
import io.raccoonwallet.app.core.storage.IntegrityChecker
import io.raccoonwallet.app.nav.RaccoonWalletNavHost
import io.raccoonwallet.app.nav.ModeSelect
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

        val integrityResult = runBlocking {
            IntegrityChecker.check(app)
        }
        if (integrityResult is IntegrityChecker.Result.Corrupted) {
            runBlocking {
                IntegrityChecker.fullReset(app)
            }
            app.reinitPublicStore()
        }

        app.appSettings.refreshDiagnostics()

        val startDestination: Any = if (integrityResult is IntegrityChecker.Result.Corrupted) {
            ModeSelect(showResetNotice = true)
        } else {
            val snapshot = (integrityResult as IntegrityChecker.Result.OK).snapshot
            val appMode = snapshot.appMode
            when {
                appMode == null -> ModeSelect()
                !snapshot.dkgComplete -> SetupDkg(isVault = appMode == AppMode.VAULT)
                appMode == AppMode.VAULT -> VaultDashboard
                else -> SignerIdle
            }
        }

        setContent {
            val navController = rememberNavController()

            // Derive theme from current route — updates reactively on navigation
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
