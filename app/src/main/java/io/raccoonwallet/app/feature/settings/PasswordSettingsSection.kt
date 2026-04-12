package io.raccoonwallet.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.storage.MasterPasswordManager
import io.raccoonwallet.app.core.storage.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Shared helpers ──

@Composable
private fun DialogStatus(working: Boolean, statusText: String, error: String) {
    if (working) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(statusText, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (error.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean = false,
    supportingText: String? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        isError = isError,
        supportingText = if (supportingText != null) {
            { Text(supportingText) }
        } else null,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    )
}

private suspend fun warmStores(app: RaccoonWalletApp): SecretStore? {
    val authMode = app.publicStore.getAuthMode()
    return if (authMode == AuthMode.NONE) {
        app.getSecretStore(authMode).also { it.readData() }
    } else null
}

private suspend fun rewriteStores(app: RaccoonWalletApp, secretStore: SecretStore?) {
    app.publicStore.rewrite()
    secretStore?.rewrite()
}

// ── Main section ──

@Composable
fun PasswordSettingsSection(app: RaccoonWalletApp) {
    val passwordManager = app.masterPasswordManager
    val isUnlocked by passwordManager.unlocked.collectAsState()
    val isConfigured = isUnlocked || passwordManager.isPasswordConfigured()
    val showSetDialog = remember { mutableStateOf(false) }
    val showChangeDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }

    if (isConfigured) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showChangeDialog.value = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Change Password")
            }
            OutlinedButton(
                onClick = { showDeleteDialog.value = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Remove Password")
            }
        }
    } else {
        Button(
            onClick = { showSetDialog.value = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set Master Password")
        }
    }

    if (showSetDialog.value) {
        SetPasswordDialog(app = app, onDismiss = { showSetDialog.value = false })
    }
    if (showDeleteDialog.value) {
        DeletePasswordDialog(app = app, onDismiss = { showDeleteDialog.value = false })
    }
    if (showChangeDialog.value) {
        ChangePasswordDialog(app = app, onDismiss = { showChangeDialog.value = false })
    }
}

// ── Dialogs ──

@Composable
private fun SetPasswordDialog(app: RaccoonWalletApp, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    val confirm = remember { mutableStateOf("") }
    val error = remember { mutableStateOf("") }
    val working = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val mismatch = confirm.value.isNotEmpty() && password != confirm.value
    val valid = !working.value &&
        password.length >= MasterPasswordManager.MIN_PASSWORD_LENGTH &&
        password == confirm.value

    AlertDialog(
        onDismissRequest = { if (!working.value) onDismiss() },
        title = { Text("Set Master Password") },
        text = {
            Column {
                Text(
                    "Your wallet will be locked with this password on every app open.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                PasswordField(password, { password = it; error.value = "" }, "Password", !working.value)
                Spacer(modifier = Modifier.height(8.dp))
                PasswordField(
                    confirm.value, { confirm.value = it; error.value = "" }, "Confirm Password", !working.value,
                    isError = mismatch,
                    supportingText = if (mismatch) "Passwords do not match" else null
                )
                DialogStatus(working.value, "Setting up encryption...", error.value)
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
                        val chars = password.toCharArray()
                        val secretStore = warmStores(app)
                        withContext(Dispatchers.Default) {
                            app.masterPasswordManager.setupPassword(chars)
                        }
                        rewriteStores(app, secretStore)
                        chars.fill('\u0000')
                        password = ""; confirm.value = ""
                        working.value = false
                        onDismiss()
                    }
                },
                enabled = valid
            ) { Text("Set Password") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(app: RaccoonWalletApp, onDismiss: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val confirm = remember { mutableStateOf("") }
    val error = remember { mutableStateOf("") }
    val working = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val mismatch = confirm.value.isNotEmpty() && newPassword != confirm.value
    val valid = !working.value && currentPassword.isNotEmpty() &&
        newPassword.length >= MasterPasswordManager.MIN_PASSWORD_LENGTH &&
        newPassword == confirm.value

    AlertDialog(
        onDismissRequest = { if (!working.value) onDismiss() },
        title = { Text("Change Master Password") },
        text = {
            Column {
                PasswordField(currentPassword, { currentPassword = it; error.value = "" }, "Current Password", !working.value)
                Spacer(modifier = Modifier.height(8.dp))
                PasswordField(newPassword, { newPassword = it; error.value = "" }, "New Password", !working.value)
                Spacer(modifier = Modifier.height(8.dp))
                PasswordField(
                    confirm.value, { confirm.value = it; error.value = "" }, "Confirm New Password", !working.value,
                    isError = mismatch,
                    supportingText = if (mismatch) "Passwords do not match" else null
                )
                DialogStatus(working.value, "Changing password...", error.value)
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
                        val oldChars = currentPassword.toCharArray()
                        val verified = withContext(Dispatchers.Default) {
                            app.masterPasswordManager.verifyPassword(oldChars)
                        }
                        if (!verified) {
                            error.value = "Current password is incorrect"
                            currentPassword = ""
                            working.value = false
                            return@launch
                        }

                        val newChars = newPassword.toCharArray()
                        val secretStore = warmStores(app)
                        val success = withContext(Dispatchers.Default) {
                            app.masterPasswordManager.changePassword(newChars)
                        }
                        if (success) {
                            rewriteStores(app, secretStore)
                            newChars.fill('\u0000')
                            currentPassword = ""; newPassword = ""; confirm.value = ""
                            working.value = false
                            onDismiss()
                        } else {
                            newChars.fill('\u0000')
                            error.value = "Failed to change password"
                            working.value = false
                        }
                    }
                },
                enabled = valid
            ) { Text("Change Password") }
        }
    )
}

@Composable
private fun DeletePasswordDialog(app: RaccoonWalletApp, onDismiss: () -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    val error = remember { mutableStateOf("") }
    val working = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!working.value) onDismiss() },
        title = { Text("Remove Master Password") },
        text = {
            Column {
                Text(
                    "Enter your current password to confirm. The app will no longer require a password on open.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                PasswordField(currentPassword, { currentPassword = it; error.value = "" }, "Current Password", !working.value)
                DialogStatus(working.value, "Removing password...", error.value)
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
                        val chars = currentPassword.toCharArray()
                        val verified = withContext(Dispatchers.Default) {
                            app.masterPasswordManager.verifyPassword(chars)
                        }
                        if (!verified) {
                            error.value = "Current password is incorrect"
                            currentPassword = ""
                            working.value = false
                            return@launch
                        }

                        val secretStore = warmStores(app)
                        app.masterPasswordManager.clearKey()
                        rewriteStores(app, secretStore)
                        app.masterPasswordManager.deletePassword()

                        currentPassword = ""
                        working.value = false
                        onDismiss()
                    }
                },
                enabled = currentPassword.isNotEmpty() && !working.value,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Remove") }
        }
    )
}
