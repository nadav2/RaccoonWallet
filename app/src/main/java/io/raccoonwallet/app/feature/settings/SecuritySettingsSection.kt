package io.raccoonwallet.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.RaccoonWalletApp
import io.raccoonwallet.app.core.storage.WipeProtection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SecuritySettingsSection(app: RaccoonWalletApp) {
    if (!app.masterPasswordManager.isPasswordConfigured()) return

    val wipeProtection = app.wipeProtection
    var autoWipeEnabled by remember { mutableStateOf(wipeProtection.isAutoWipeEnabled()) }
    val showDuressDialog = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Security", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        // Auto-wipe toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wipe after ${WipeProtection.MAX_ATTEMPTS} wrong attempts")
                Text(
                    "Delete all keys and data after ${WipeProtection.MAX_ATTEMPTS} consecutive wrong password attempts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoWipeEnabled,
                onCheckedChange = {
                    autoWipeEnabled = it
                    wipeProtection.setAutoWipeEnabled(it)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Duress code
        Button(
            onClick = { showDuressDialog.value = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (wipeProtection.isDuressCodeConfigured()) "Change Duress Code"
                else "Set Duress Code"
            )
        }
        Text(
            "A special code that silently wipes all data when entered on the lock screen",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDuressDialog.value) {
        DuressCodeDialog(
            wipeProtection = wipeProtection,
            onDismiss = { showDuressDialog.value = false }
        )
    }
}

@Composable
private fun DuressCodeDialog(wipeProtection: WipeProtection, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val confirm = remember { mutableStateOf("") }
    val working = remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val mismatch = confirm.value.isNotEmpty() && code != confirm.value
    val tooShort = code.isNotEmpty() && code.length < WipeProtection.MIN_DURESS_LENGTH
    val valid = !working.value &&
        code.length >= WipeProtection.MIN_DURESS_LENGTH &&
        code == confirm.value

    AlertDialog(
        onDismissRequest = { if (!working.value) onDismiss() },
        title = { Text("Set Duress Code") },
        text = {
            Column {
                Text(
                    "This code will silently wipe all wallet data when entered on the lock screen.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = code,
                    onValueChange = { code = it; error = "" },
                    label = { Text("Duress Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = tooShort,
                    supportingText = if (tooShort) {
                        { Text("Minimum ${WipeProtection.MIN_DURESS_LENGTH} characters") }
                    } else null,
                    singleLine = true,
                    enabled = !working.value,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = confirm.value,
                    onValueChange = { confirm.value = it; error = "" },
                    label = { Text("Confirm Duress Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text("Codes do not match") }
                    } else null,
                    singleLine = true,
                    enabled = !working.value,
                    modifier = Modifier.fillMaxWidth()
                )
                if (working.value) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Setting up duress code...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working.value) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        working.value = true
                        val chars = code.toCharArray()
                        withContext(Dispatchers.Default) {
                            wipeProtection.setupDuressCode(chars)
                        }
                        code = ""
                        confirm.value = ""
                        working.value = false
                        onDismiss()
                    }
                },
                enabled = valid
            ) { Text("Set Code") }
        }
    )
}
