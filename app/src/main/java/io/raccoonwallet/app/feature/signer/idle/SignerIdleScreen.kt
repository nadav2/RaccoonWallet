package io.raccoonwallet.app.feature.signer.idle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.ui.components.RaccoonWalletTopBar
import io.raccoonwallet.app.ui.components.QrScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignerIdleScreen(
    onSignRequest: (sessionId: String, transportMode: TransportMode) -> Unit,
    onSettings: () -> Unit,
    viewModel: SignerIdleViewModel = viewModel()
) {
    var showScanner by remember { mutableStateOf(false) }

    // Auto-navigate when an NFC or QR sign request arrives
    LaunchedEffect(Unit) {
        viewModel.incomingRequest.collect { pending ->
            showScanner = false
            onSignRequest(pending.request.sessionId, pending.transportMode)
        }
    }

    Scaffold(
        topBar = {
            RaccoonWalletTopBar(
                title = "Raccoon Wallet Signer",
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showScanner) {
                Text(
                    "Scan Vault's QR code",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                QrScanner(
                    onFrameScanned = { viewModel.onQrFrameScanned(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = {
                    showScanner = false
                    viewModel.stopQrScan()
                }) {
                    Text("Cancel")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Waiting for Vault...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "NFC service is active. The Vault device will connect when ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(onClick = {
                    showScanner = true
                    viewModel.startQrScan()
                }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Scan QR Instead")
                }
            }
        }
    }
}
