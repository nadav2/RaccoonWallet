package io.raccoonwallet.app.feature.vault.sign

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.core.model.SignState
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.ui.components.FlowErrorCard
import io.raccoonwallet.app.ui.components.RaccoonWalletTopBar
import io.raccoonwallet.app.ui.components.NfcWaitHint
import io.raccoonwallet.app.ui.components.QrFrameDisplay
import io.raccoonwallet.app.ui.components.QrScanner
import io.raccoonwallet.app.ui.components.SetupStepIndicator

@Composable
fun VaultSignScreen(
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: VaultSignViewModel = viewModel()
) {
    val state by viewModel.signState.collectAsState()
    val activity = LocalActivity.current as FragmentActivity

    Scaffold(
        topBar = {
            RaccoonWalletTopBar(
                title = "Sign Transaction",
                onBack = if (state is SignState.Complete) null else ({ onCancel() })
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Step indicator
            val step = signStep(state)
            if (step != null) {
                SetupStepIndicator(currentStep = step, totalSteps = 5)
                Spacer(modifier = Modifier.height(16.dp))
            }

            when (val s = state) {
                is SignState.Idle, is SignState.BuildingTransaction -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Preparing transaction...")
                }

                is SignState.ChoosingTransport -> TransportPicker(
                    onSelectNfc = {
                        viewModel.selectTransport(TransportMode.NFC)
                        viewModel.doNfcTap1(activity)
                    },
                    onSelectQr = { viewModel.selectTransport(TransportMode.QR) }
                )

                is SignState.AwaitingTap1 -> NfcPulseWithHint(
                    title = "Tap 1: Send Request",
                    message = "Hold near Signer device",
                    warningMessage = "Still waiting... hold phones together",
                    warningAfterSeconds = 10
                )

                is SignState.DisplayingQr -> {
                    Text("Show this to Signer", style = MaterialTheme.typography.titleMedium)
                    if (s.fingerprint != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Connection: ${s.fingerprint}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Verify this matches on Signer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    QrFrameDisplay(frames = s.frames)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.onQrDisplayDone() }) {
                        Text("Signer has scanned")
                    }
                }

                is SignState.RequestDelivered, is SignState.WaitingForApproval -> {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Waiting for Signer approval...", style = MaterialTheme.typography.titleMedium)
                    if (s is SignState.WaitingForApproval && s.fingerprint != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Connection: ${s.fingerprint}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Verify this matches on Signer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Approve the transaction on the Signer device, then tap again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.doNfcTap2(activity) }) {
                        Text("Tap 2: Receive Signature")
                    }
                }

                is SignState.AwaitingTap2 -> NfcPulseWithHint(
                    title = "Tap 2: Receive Signature",
                    message = "Hold near Signer device again",
                    warningMessage = "Still waiting... hold phones together",
                    warningAfterSeconds = 10
                )

                is SignState.ScanningQr -> {
                    Text("Scan Signer's response QR", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    QrScanner(
                        onFrameScanned = { viewModel.onQrFrameScanned(it, activity) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    if (s.progress > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is SignState.Finalizing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Finalizing signature & broadcasting...")
                }

                is SignState.Complete -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Transaction Sent!", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = s.txHash,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                }

                is SignState.Rejected -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(s.reason, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = onCancel) { Text("Back") }
                }

                is SignState.Failed -> {
                    FlowErrorCard(
                        error = s.error,
                        onStartOver = onCancel
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportPicker(
    onSelectNfc: () -> Unit,
    onSelectQr: () -> Unit
) {
    Text("Choose Transport", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(24.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            onClick = onSelectNfc,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("NFC", style = MaterialTheme.typography.titleMedium)
                Text("Tap devices", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(
            onClick = onSelectQr,
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("QR Code", style = MaterialTheme.typography.titleMedium)
                Text("Scan codes", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NfcPulseWithHint(
    title: String,
    message: String,
    warningMessage: String,
    warningAfterSeconds: Int
) {
    Icon(
        imageVector = Icons.Default.Nfc,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    NfcWaitHint(
        message = message,
        warningMessage = warningMessage,
        warningAfterSeconds = warningAfterSeconds
    )
    Spacer(modifier = Modifier.height(16.dp))
    CircularProgressIndicator()
}

/**
 * Map sign state to step number for the progress indicator.
 * 5 steps: Prepare -> Send -> Approve -> Receive -> Broadcast
 */
private fun signStep(state: SignState): Int? = when (state) {
    is SignState.Idle, is SignState.BuildingTransaction, is SignState.ChoosingTransport -> 1
    is SignState.AwaitingTap1, is SignState.DisplayingQr -> 2
    is SignState.RequestDelivered, is SignState.WaitingForApproval -> 3
    is SignState.AwaitingTap2, is SignState.ScanningQr -> 4
    is SignState.Finalizing, is SignState.Complete -> 5
    else -> null
}
