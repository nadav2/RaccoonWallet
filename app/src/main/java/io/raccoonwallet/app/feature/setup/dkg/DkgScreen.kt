package io.raccoonwallet.app.feature.setup.dkg

import androidx.activity.compose.LocalActivity
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.core.crypto.TapEntropyCollector
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.model.DkgState
import io.raccoonwallet.app.core.model.TransportMode
import io.raccoonwallet.app.core.storage.BiometricGate
import io.raccoonwallet.app.core.transport.qr.QrEncoder
import io.raccoonwallet.app.ui.components.AnimatedQrDisplay
import io.raccoonwallet.app.ui.components.FlowErrorCard
import io.raccoonwallet.app.ui.components.NfcWaitHint
import io.raccoonwallet.app.ui.components.QrScanner
import io.raccoonwallet.app.ui.components.SecureWindowEffect

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

    SecureWindowEffect(enabled = true)

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
            val totalSteps = if (isVault) 5 else 6
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

            is DkgState.ChoosingSeedGeneration -> {
                SeedGenerationPicker(
                    onGenerateNormally = { viewModel.startStandardSeedGeneration() },
                    onImport = { viewModel.showImportSeed() }
                )
            }

            is DkgState.CollectingEntropy -> {
                TapEntropyView(
                    tapCount = s.tapCount,
                    progress = s.progress,
                    isReady = s.isReady,
                    onTap = { xPx, yPx, widthPx, heightPx, eventTimeNanos ->
                        viewModel.recordEntropyTap(
                            xPx = xPx,
                            yPx = yPx,
                            widthPx = widthPx,
                            heightPx = heightPx,
                            eventTimeNanos = eventTimeNanos
                        )
                    },
                    onContinue = { viewModel.finalizeEntropyCollection() },
                    onBack = { viewModel.cancelEntropyCollection() }
                )
            }

            is DkgState.GeneratingSeed -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generating recovery phrase...")
            }

            is DkgState.ShowingSeed -> {
                SeedBackupView(
                    words = s.words,
                    onConfirmed = { viewModel.onSeedConfirmed() },
                    onImport = { viewModel.showImportSeed() },
                    onAddEntropy = { viewModel.startEntropyFromSeedScreen() }
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
                    Text("Back to setup options")
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

            is DkgState.ChoosingPassword -> {
                PasswordSetupContent(
                    onPasswordSet = { viewModel.setMasterPassword(it) },
                    onSkip = { viewModel.skipMasterPassword() }
                )
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
private fun SeedGenerationPicker(
    onGenerateNormally: () -> Unit,
    onImport: () -> Unit
) {
    Text("Create or restore wallet", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "This setup screen is protected from screenshots while you generate or import your recovery phrase.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    Button(onClick = onGenerateNormally, modifier = Modifier.fillMaxWidth()) {
        Text("Generate recovery phrase")
    }
    Spacer(modifier = Modifier.height(20.dp))
    TextButton(onClick = onImport) {
        Text("I already have a recovery phrase")
    }
}

private class TapRipple(
    val center: Offset,
    val scale: Animatable<Float, AnimationVector1D> = Animatable(0f),
    val alpha: Animatable<Float, AnimationVector1D> = Animatable(0.6f)
)

@Composable
private fun TapEntropyView(
    tapCount: Int,
    progress: Float,
    isReady: Boolean,
    onTap: (Float, Float, Float, Float, Long) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val requiredTaps = TapEntropyCollector.DEFAULT_TARGET_TAP_COUNT
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val ripples = remember { mutableStateListOf<TapRipple>() }

    // Animated background color shifts as progress increases
    val baseColor = MaterialTheme.colorScheme.primaryContainer
    val bgColor by animateColorAsState(
        targetValue = lerp(
            baseColor.copy(alpha = 0.3f),
            baseColor,
            progress
        ),
        animationSpec = tween(300),
        label = "entropy_bg"
    )
    val borderColor = MaterialTheme.colorScheme.primary
    val rippleColor = MaterialTheme.colorScheme.primary

    Text("Add Entropy", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Tap the pad to stir in extra randomness. Your taps are mixed into the seed alongside secure device randomness.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(28.dp)
            )
            .pointerInput(onTap) {
                detectTapGestures { offset ->
                    onTap(
                        offset.x,
                        offset.y,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        System.nanoTime()
                    )
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    // Spawn ripple
                    val ripple = TapRipple(center = offset)
                    ripples.add(ripple)
                    scope.launch {
                        kotlinx.coroutines.coroutineScope {
                            launch { ripple.scale.animateTo(3f, tween(600)) }
                            launch { ripple.alpha.animateTo(0f, tween(600)) }
                        }
                        ripples.remove(ripple)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Ripple canvas
        val rippleRadiusPx = with(density) { 40.dp.toPx() }
        val strokeWidthPx = with(density) { 2.dp.toPx() }
        Canvas(modifier = Modifier.matchParentSize()) {
            for (ripple in ripples) {
                drawCircle(
                    color = rippleColor.copy(alpha = ripple.alpha.value),
                    radius = rippleRadiusPx * ripple.scale.value,
                    center = ripple.center,
                    style = Stroke(width = strokeWidthPx)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tap anywhere", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))

            // Animated counter
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = tapCount,
                    transitionSpec = {
                        (slideInVertically { it } togetherWith slideOutVertically { -it })
                            .using(SizeTransform(clip = false))
                    },
                    label = "tap_counter"
                ) { count ->
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    " / $requiredTaps taps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        if (isReady) {
            "Enough entropy captured. Generate your recovery phrase when ready."
        } else {
            "Keep tapping until the meter is full."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onContinue,
        enabled = isReady,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Generate recovery phrase")
    }
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onBack) {
        Text("Cancel")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedBackupView(
    words: List<String>,
    onConfirmed: () -> Unit,
    onImport: () -> Unit,
    onAddEntropy: () -> Unit
) {
    // Resets to hidden when a new word list is generated (e.g. after adding entropy)
    var revealed by remember(words) { mutableStateOf(false) }
    val blurRadius by animateFloatAsState(
        targetValue = if (revealed) 0f else 16f,
        animationSpec = tween(durationMillis = 400),
        label = "blur_radius"
    )

    Text("Write down your recovery phrase", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Store these 12 words safely. They are the only way to recover your wallet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(24.dp))

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (blurRadius > 0f) Modifier.blur(blurRadius.dp)
                    else Modifier
                )
        ) {
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

        // Overlay scrim when not revealed
        AnimatedVisibility(
            visible = !revealed,
            exit = fadeOut(tween(300)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .pointerInput(Unit) { detectTapGestures { revealed = true } },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap to reveal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (revealed) {
        Button(onClick = onConfirmed, modifier = Modifier.fillMaxWidth()) {
            Text("I've written it down")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedButton(onClick = onAddEntropy, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Default.Shuffle,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add Entropy")
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Mix your taps into the seed for extra randomness",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (revealed) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onImport) {
            Text("I already have a recovery phrase")
        }
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
 * Signer (6 steps): Seed -> Security -> Password -> Transport -> Transfer -> Done
 * Vault (5 steps): Security -> Password -> Connect -> Receive -> Done
 */
private fun dkgStep(state: DkgState, isVault: Boolean): Int? {
    if (isVault) {
        return when (state) {
            is DkgState.ChoosingBiometric -> 1
            is DkgState.ChoosingPassword -> 2
            is DkgState.WaitingForVault -> 3
            is DkgState.AwaitingNfcTap, is DkgState.ReceivingNfc,
            is DkgState.ScanningQr -> 4
            is DkgState.AwaitingBiometric, is DkgState.StoringKeys,
            is DkgState.DisplayingAck, is DkgState.Complete -> 5
            else -> null
        }
    } else {
        return when (state) {
            is DkgState.ChoosingSeedGeneration,
            is DkgState.CollectingEntropy,
            is DkgState.GeneratingSeed,
            is DkgState.ShowingSeed,
            is DkgState.ImportingSeed -> 1
            is DkgState.SeedConfirmed, is DkgState.GeneratingPaillier,
            is DkgState.SplittingKeys, is DkgState.ChoosingBiometric -> 2
            is DkgState.ChoosingPassword -> 3
            is DkgState.ChoosingTransport -> 4
            is DkgState.AwaitingNfcTap, is DkgState.ReceivingNfc,
            is DkgState.DisplayingQr, is DkgState.ScanningQr -> 5
            is DkgState.AwaitingBiometric, is DkgState.StoringKeys,
            is DkgState.Complete -> 6
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
    val scale = remember { Animatable(0f) }
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
