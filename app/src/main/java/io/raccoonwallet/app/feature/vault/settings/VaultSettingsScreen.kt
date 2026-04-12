package io.raccoonwallet.app.feature.vault.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.deps
import io.raccoonwallet.app.feature.settings.PasswordSettingsSection
import io.raccoonwallet.app.feature.settings.SecuritySettingsSection
import io.raccoonwallet.app.ui.components.DiagnosticsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSettingsScreen(
    onModeReset: () -> Unit,
    onBack: () -> Unit
) {
    val app = (LocalContext.current.applicationContext as android.app.Application).deps
    val appSettings = app.appSettings
    val diagnostics by appSettings.diagnostics.collectAsState()

    val showResetConfirm = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appSettings.refreshDiagnostics() }

    if (showResetConfirm.value) {
        ResetConfirmDialog(
            onDismiss = { showResetConfirm.value = false },
            onConfirm = { appSettings.resetMode(); onModeReset() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Mode: Vault", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            diagnostics?.let { DiagnosticsCard(data = it) }

            Spacer(modifier = Modifier.height(16.dp))
            PasswordSettingsSection(app = app)

            Spacer(modifier = Modifier.height(16.dp))
            SecuritySettingsSection(app = app)

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { showResetConfirm.value = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset App (Wipe All Keys)")
            }

            Spacer(modifier = Modifier.height(32.dp))
            diagnostics?.let {
                Text(
                    text = "Raccoon Wallet v${it.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResetConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var confirmText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset App?") },
        text = {
            Column {
                Text("This will permanently wipe all keys and wallet data. This cannot be undone.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type NUKEIT to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmText == "NUKEIT",
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Wipe All Keys") }
        }
    )
}
