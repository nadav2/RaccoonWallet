package io.raccoonwallet.app.feature.setup.dkg

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.model.DkgState
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.storage.BiometricGate
import io.raccoonwallet.app.core.transport.qr.QrEncoder
import io.raccoonwallet.app.ui.components.AnimatedQrDisplay
import io.raccoonwallet.app.ui.components.FlowErrorCard
import io.raccoonwallet.app.ui.components.NfcWaitHint
import io.raccoonwallet.app.ui.components.QrScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DkgScreen(
    onDkgComplete: (isVault: Boolean) -> Unit,
    onBack: () -> Unit = {},
    viewModel: DkgViewModel = viewModel()
) {
    val state by viewModel.dkgState.collectAsState()
    val isVault = viewModel.isVaultMode
    val activity = LocalActivity.current as? androidx.fragment.app.FragmentActivity
    val deviceHasBiometric = remember(activity) {
        activity?.let { BiometricGate.hasBiometric(it) } ?: false
    }

    val canGoBack = state !is DkgState.StoringKeys && state !is DkgState.Complete
    val showCancelDialog = remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { showCancelDialog.value = true }

    Scaffold(
        topBar = {
            if (canGoBack) {
                TopAppBar(
                    title = { Text("Wallet Setup") },
                    navigationIcon = {
                        IconButton(onClick = { showCancelDialog.value = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            }
        }
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Step indicator
        val step = dkgStep(state, isVault)
        if (step != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val totalSteps = if (isVault) 4 else 5
            io.raccoonwallet.app.ui.components.SetupStepIndicator(
                currentStep = step,
                totalSteps = totalSteps
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        var selectedAuthMode by remember { mutableStateOf(AuthMode.NONE) }

        LaunchedEffect(deviceHasBiometric) {
            if (!deviceHasBiometric) selectedAuthMode = AuthMode.NONE
        }

        when (val s = state) {
            is DkgState.Idle -> {
                CircularProgressIndicator()
            }

            is DkgState.ShowingSeed -> {
                SeedBackupView(
                    words = s.words,
                    onConfirmed = { viewModel.onSeedConfirmed() },
                    onImport = { viewModel.showImportSeed() }
                )
            }

            is DkgState.ImportingSeed -> {
                var seedInput by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }

                Text("Import existing wallet", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enter your 12-word recovery phrase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = seedInput,
                    onValueChange = { seedInput = it; error = null },
                    label = { Text("Recovery phrase") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    placeholder = { Text("word1 word2 word3 ...") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val words = seedInput.trim().lowercase().split("\\s+".toRegex())
                        if (words.size != 12) {
                            error = if (words.size == 24)
                                "24-word phrases are valid but Raccoon Wallet only supports 12 words"
                            else
                                "Expected 12 words, got ${words.size}"
                        } else {
                            viewModel.startSignerImport(words)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = seedInput.isNotBlank()
                ) {
                    Text("Restore")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { viewModel.startSignerDkg() }
                ) {
                    Text("Generate new wallet instead")
                }
            }

            is DkgState.SeedConfirmed,
            is DkgState.GeneratingPaillier,
            is DkgState.SplittingKeys,
            is DkgState.ChoosingBiometric -> {
                val isGenerating = state !is DkgState.ChoosingBiometric
                val bioEnabled = selectedAuthMode != AuthMode.NONE

                Text("Security", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require biometric", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (deviceHasBiometric) {
                                "Fingerprint or face required to access key shares"
                            } else {
                                "Unavailable on this device. Set up fingerprint or face to enable this."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = bioEnabled,
                        onCheckedChange = {
                            selectedAuthMode = if (it) AuthMode.BIOMETRIC_OR_DEVICE else AuthMode.NONE
                        },
                        enabled = !isGenerating && deviceHasBiometric
                    )
                }

                if (bioEnabled && deviceHasBiometric) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Authentication mode", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    AuthModeOption(
                        selected = selectedAuthMode == AuthMode.BIOMETRIC_OR_DEVICE,
                        onClick = { selectedAuthMode = AuthMode.BIOMETRIC_OR_DEVICE },
                        enabled = !isGenerating,
                        title = AuthMode.BIOMETRIC_OR_DEVICE.displayName,
                        description = "Fingerprint, face, or device PIN"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AuthModeOption(
                        selected = selectedAuthMode == AuthMode.BIOMETRIC_ONLY,
                        onClick = { selectedAuthMode = AuthMode.BIOMETRIC_ONLY },
                        enabled = !isGenerating,
                        title = AuthMode.BIOMETRIC_ONLY.displayName,
                        description = "Stronger security. Adding or removing fingerprints will require a full wallet reset."
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.setAuthMode(selectedAuthMode) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating keys...")
                    } else {
                        Text("Continue")
                    }
                }
            }

            is DkgState.ChoosingTransport -> {
                TransportPicker(
                    onSelected = { viewModel.selectTransport(it) }
                )
            }

            is DkgState.AwaitingNfcTap -> {
                NfcPulseIcon()
                Spacer(modifier = Modifier.height(16.dp))
                if (isVault) {
                    Text("Hold phone to Signer device", style = MaterialTheme.typography.titleMedium)
                    NfcWaitHint(
                        message = "Keep phones together until transfer completes",
                        warningMessage = "Still waiting... make sure both devices have NFC enabled"
                    )
                } else {
                    Text("Ready for NFC", style = MaterialTheme.typography.titleMedium)
                    NfcWaitHint(
                        message = "The Vault device should tap this phone now",
                        warningMessage = "Still waiting... make sure the Vault app is open"
                    )
                }
            }

            is DkgState.ReceivingNfc -> {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Receiving keys via NFC...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Keep phones together",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(s.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is DkgState.DisplayingQr -> {
                val encoder = remember { QrEncoder() }
                val bitmaps = remember(s.frames) {
                    s.frames.map { encoder.frameToBitmap(it) }
                }

                Text("Show this to the Vault device", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Make sure no one else can see your screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedQrDisplay(frames = bitmaps)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.onQrDisplayDone() }) {
                    Text("Vault has scanned it")
                }
            }

            is DkgState.ScanningQr -> {
                if (!isVault) {
                    // Signer scanning Vault's ACK
                    Text("Scan ACK from Vault", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    QrScanner(
                        onFrameScanned = { raw -> viewModel.onVaultAckScanned(raw) },
                        progress = s.progress,
                        modifier = Modifier.fillMaxWidth().height(400.dp)
                    )
                } else {
                    // Vault scanning Signer's bundle
                    Text("Scan QR from Signer", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    QrScanner(
                        onFrameScanned = { raw -> viewModel.onVaultQrFrameScanned(raw) },
                        progress = s.progress,
                        modifier = Modifier.fillMaxWidth().height(400.dp)
                    )
                }
            }

            is DkgState.WaitingForVault -> {
                val activity = LocalActivity.current as android.app.Activity
                VaultWaitingView(
                    onScanQr = { viewModel.startVaultQrScan() },
                    onNfcReceive = { viewModel.startVaultNfcReceive(activity) }
                )
            }

            is DkgState.AwaitingBiometric -> {
                val activity = LocalActivity.current as androidx.fragment.app.FragmentActivity
                LaunchedEffect(Unit) {
                    viewModel.authenticateAndStore(activity)
                }
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Authenticating...")
            }

            is DkgState.DisplayingAck -> {
                // Vault shows ACK QR for Signer to scan
                val encoder = remember { QrEncoder() }
                val bitmaps = remember(s.frames) {
                    s.frames.map { encoder.frameToBitmap(it) }
                }

                Text(
                    text = "Keys stored successfully!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Show this ACK to the Signer device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedQrDisplay(frames = bitmaps)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.onVaultConfirmed() }) {
                    Text("Done")
                }
            }

            is DkgState.SignerReceived -> {
                // Signer side — shouldn't reach this in current flow but keep as safety
                Text(
                    text = "Received ${s.accountCount} accounts!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.onVaultConfirmed() }) {
                    Text("Continue")
                }
            }

            is DkgState.StoringKeys -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Storing keys securely...")
            }

            is DkgState.Complete -> {
                SuccessAnimation()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Setup Complete!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${DkgViewModel.ACCOUNT_COUNT} accounts generated and paired")
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onDkgComplete(isVault) }) {
                    Text("Continue")
                }
            }

            is DkgState.Failed -> {
                FlowErrorCard(
                    error = s.error,
                    onRetry = { viewModel.retryTransport() },
                    onStartOver = {
                        viewModel.cancel()
                        if (isVault) viewModel.startVaultDkg() else viewModel.startSignerDkg()
                    }
                )
            }
        }
    }
    } // Scaffold

    if (showCancelDialog.value) {
        AlertDialog(
            onDismissRequest = { showCancelDialog.value = false },
            title = { Text("Are you sure?") },
            text = { Text("All progress will be lost.") },
            dismissButton = {
                TextButton(onClick = { showCancelDialog.value = false }) {
                    Text("No")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog.value = false
                    viewModel.cancel()
                    onBack()
                }) {
                    Text("Yes")
                }
            },
            modifier = Modifier.fillMaxWidth(1f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedBackupView(
    words: List<String>,
    onConfirmed: () -> Unit,
    onImport: () -> Unit
) {
    Text("Write down your recovery phrase", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Store these 12 words safely. They are the only way to recover your wallet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            words.forEachIndexed { index, word ->
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "${index + 1}. $word",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onConfirmed, modifier = Modifier.fillMaxWidth()) {
        Text("I've written it down")
    }
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onImport) {
        Text("I already have a recovery phrase")
    }
}

@Composable
private fun TransportPicker(onSelected: (TransportMode) -> Unit) {
    Text("How to pair with Vault?", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { onSelected(TransportMode.NFC) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Nfc, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("NFC (Recommended)")
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = { onSelected(TransportMode.QR) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.QrCode, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("QR Code")
    }
}

@Composable
private fun VaultWaitingView(onScanQr: () -> Unit, onNfcReceive: () -> Unit) {
    Icon(
        imageVector = Icons.Default.Nfc,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text("Waiting for Signer...", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Choose how to receive keys from the Signer device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onNfcReceive,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Nfc, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("NFC (Recommended)")
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(
        onClick = onScanQr,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.QrCode, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Scan QR")
    }
}

/**
 * Map DKG state to step number for the progress indicator.
 * Returns null for states that shouldn't show a step (Idle, Failed).
 *
 * Signer (5 steps): Seed -> Security -> Transport -> Transfer -> Done
 * Vault (4 steps): Security -> Connect -> Receive -> Done
 */
private fun dkgStep(state: DkgState, isVault: Boolean): Int? {
    if (isVault) {
        return when (state) {
            is DkgState.ChoosingBiometric -> 1
            is DkgState.WaitingForVault -> 2
            is DkgState.AwaitingNfcTap, is DkgState.ReceivingNfc,
            is DkgState.ScanningQr -> 3
            is DkgState.AwaitingBiometric, is DkgState.StoringKeys,
            is DkgState.DisplayingAck, is DkgState.Complete -> 4
            else -> null
        }
    } else {
        return when (state) {
            is DkgState.ShowingSeed, is DkgState.ImportingSeed -> 1
            is DkgState.SeedConfirmed, is DkgState.GeneratingPaillier,
            is DkgState.SplittingKeys, is DkgState.ChoosingBiometric -> 2
            is DkgState.ChoosingTransport -> 3
            is DkgState.AwaitingNfcTap, is DkgState.ReceivingNfc,
            is DkgState.DisplayingQr, is DkgState.ScanningQr -> 4
            is DkgState.AwaitingBiometric, is DkgState.StoringKeys,
            is DkgState.Complete -> 5
            else -> null
        }
    }
}

/** NFC icon with pulsing ripple rings */
@Composable
private fun NfcPulseIcon() {
    val transition = rememberInfiniteTransition(label = "nfc_pulse")
    val scale1 by transition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "ring1_scale"
    )
    val alpha1 by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "ring1_alpha"
    )
    val scale2 by transition.animateFloat(
        initialValue = 1f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            tween(1500, delayMillis = 500), RepeatMode.Restart
        ),
        label = "ring2_scale"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1500, delayMillis = 500), RepeatMode.Restart
        ),
        label = "ring2_alpha"
    )

    val color = MaterialTheme.colorScheme.primary

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        // Ripple rings
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale1)
                .alpha(alpha1)
                .clip(CircleShape)
                .border(2.dp, color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale2)
                .alpha(alpha2)
                .clip(CircleShape)
                .border(2.dp, color, CircleShape)
        )
        // Center icon
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = color
        )
    }
}

@Composable
private fun AuthModeOption(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Animated checkmark that scales in with a bounce on the Complete screen */
@Composable
private fun SuccessAnimation() {
    val scale = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.5f,
                stiffness = 300f
            )
        )
    }

    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = "Success",
        modifier = Modifier
            .size(96.dp)
            .scale(scale.value),
        tint = MaterialTheme.colorScheme.primary
    )
}
