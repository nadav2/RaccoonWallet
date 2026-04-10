package io.raccoonwallet.app.feature.modeselect

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.core.model.AppMode

@Composable
fun ModeSelectScreen(
    showResetNotice: Boolean = false,
    onModeSelected: (AppMode) -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) { Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Raccoon Wallet",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose how this device will be used",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showResetNotice) {
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Wallet data was reset due to corrupted local state. Restore with your recovery phrase if needed.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Vault card — always orange, independent of theme
        val vaultBg = if (isSystemInDarkTheme())
            io.raccoonwallet.app.ui.theme.VaultPrimaryContainerDark
        else
            io.raccoonwallet.app.ui.theme.VaultPrimaryContainerLight
        val vaultFg = if (isSystemInDarkTheme())
            io.raccoonwallet.app.ui.theme.VaultOnPrimaryContainerDark
        else
            io.raccoonwallet.app.ui.theme.VaultOnPrimaryContainerLight

        Card(
            onClick = { onModeSelected(AppMode.VAULT) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = vaultBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Wallet, contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = vaultFg)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Vault", style = MaterialTheme.typography.titleLarge, color = vaultFg)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Online wallet. View balances, build transactions, connect to dApps. Requires a paired Signer device to sign.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vaultFg.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Signer card — always slate blue, independent of theme
        val signerBg = if (isSystemInDarkTheme())
            io.raccoonwallet.app.ui.theme.SignerPrimaryContainerDark
        else
            io.raccoonwallet.app.ui.theme.SignerPrimaryContainerLight
        val signerFg = if (isSystemInDarkTheme())
            io.raccoonwallet.app.ui.theme.SignerOnPrimaryContainerDark
        else
            io.raccoonwallet.app.ui.theme.SignerOnPrimaryContainerLight

        Card(
            onClick = { onModeSelected(AppMode.SIGNER) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = signerBg)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PhonelinkLock, contentDescription = null,
                    modifier = Modifier.size(48.dp), tint = signerFg)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Signer", style = MaterialTheme.typography.titleLarge, color = signerFg)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Air-gapped co-signer. Keep offline with airplane mode. Approves transactions via NFC tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = signerFg.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }

    } }
}
