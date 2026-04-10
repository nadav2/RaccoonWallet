package io.raccoonwallet.app.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.raccoonwallet.app.core.model.AppMode
import io.raccoonwallet.app.feature.modeselect.ModeSelectScreen
import io.raccoonwallet.app.feature.setup.dkg.DkgScreen
import io.raccoonwallet.app.feature.vault.dashboard.DashboardScreen
import io.raccoonwallet.app.feature.vault.send.SendScreen
import io.raccoonwallet.app.feature.vault.receive.ReceiveScreen
import io.raccoonwallet.app.feature.vault.sign.VaultSignScreen
import io.raccoonwallet.app.feature.vault.history.TransactionHistoryScreen
import io.raccoonwallet.app.feature.vault.settings.VaultSettingsScreen
import io.raccoonwallet.app.feature.signer.idle.SignerIdleScreen
import io.raccoonwallet.app.feature.signer.confirm.SignerConfirmScreen
import io.raccoonwallet.app.feature.signer.settings.SignerSettingsScreen

/** Navigate to a destination, clearing the entire backstack. */
private fun NavHostController.navigateClean(dest: Any) {
    navigate(dest) { popUpTo(0) { inclusive = true } }
}

@Composable
fun RaccoonWalletNavHost(
    navController: NavHostController,
    startDestination: Any
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        // ── Setup Flow ──

        composable<ModeSelect> { backStackEntry ->
            val route = backStackEntry.toRoute<ModeSelect>()
            ModeSelectScreen(
                showResetNotice = route.showResetNotice,
                onModeSelected = { mode ->
                    navController.navigateClean(SetupDkg(isVault = mode == AppMode.VAULT))
                }
            )
        }

        composable<SetupDkg> {
            DkgScreen(
                onDkgComplete = { isVault ->
                    navController.navigateClean(if (isVault) VaultDashboard else SignerIdle)
                },
                onBack = {
                    navController.navigateClean(ModeSelect())
                }
            )
        }

        // ── Vault ──

        composable<VaultDashboard> {
            DashboardScreen(
                onSend = { chainId, accountIndex ->
                    navController.navigate(VaultSend(chainId, accountIndex))
                },
                onReceive = { accountIndex ->
                    navController.navigate(VaultReceive(accountIndex))
                },
                onSettings = { navController.navigate(VaultSettings) },
                onViewHistory = { navController.navigate(TransactionHistory) }
            )
        }

        composable<TransactionHistory> {
            TransactionHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<VaultSend> {
            SendScreen(
                onSignRequired = { vaultSign -> navController.navigate(vaultSign) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<VaultReceive> {
            ReceiveScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<VaultSign> {
            VaultSignScreen(
                onComplete = { navController.navigateClean(VaultDashboard) },
                onCancel = { navController.popBackStack() }
            )
        }

        composable<VaultSettings> {
            VaultSettingsScreen(
                onModeReset = { navController.navigateClean(ModeSelect()) },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Signer ──

        composable<SignerIdle> {
            SignerIdleScreen(
                onSignRequest = { sessionId, transportMode ->
                    navController.navigate(SignerConfirm(sessionId, transportMode))
                },
                onSettings = { navController.navigate(SignerSettings) }
            )
        }

        composable<SignerConfirm> {
            SignerConfirmScreen(
                onDone = { navController.popBackStack() }
            )
        }

        composable<SignerSettings> {
            SignerSettingsScreen(
                onModeReset = { navController.navigateClean(ModeSelect()) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
