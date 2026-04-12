package io.raccoonwallet.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.RaccoonWalletApp
import io.raccoonwallet.app.core.model.AuthMode
import io.raccoonwallet.app.core.storage.MasterPasswordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasswordSettingsSection(app: RaccoonWalletApp) {
    val passwordManager = app.masterPasswordManager
    // Observe unlocked state so this recomposes when password is set/deleted
    val isUnlocked by passwordManager.unlocked.collectAsState()
    val isConfigured = isUnlocked || passwordManager.isPasswordConfigured()
    val showSetDialog = remember { mutableStateOf(false) }
    val showChangeDialog = remember { mutableStateOf(false) }

    if (isConfigured) {
        Button(
            onClick = { showChangeDialog.value = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Change Master Password")
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

    if (showChangeDialog.value) {
        ChangePasswordDialog(app = app, onDismiss = { showChangeDialog.value = false })
    }
}

@Composable
private fun SetPasswordDialog(app: RaccoonWalletApp, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    val confirm = remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val mismatch = confirm.value.isNotEmpty() && password != confirm.value
    val valid = password.length >= MasterPasswordManager.MIN_PASSWORD_LENGTH && password == confirm.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Master Password") },
        text = {
            Column {
                Text(
                    "Your wallet will be locked with this password on every app open.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = confirm.value,
                    onValueChange = { confirm.value = it; error = "" },
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text("Passwords do not match") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val chars = password.toCharArray()
                        // Warm caches BEFORE setting password, so rewrite() reads
                        // from cache (no key needed) and writes with the new key.
                        val authMode = app.publicStore.getAuthMode()
                        val secretStore = if (authMode == AuthMode.NONE) {
                            app.getSecretStore(authMode).also { it.readData() }
                        } else null

                        withContext(Dispatchers.Default) {
                            app.masterPasswordManager.setupPassword(chars)
                        }
                        app.publicStore.rewrite()
                        secretStore?.rewrite()
                        chars.fill('\u0000')
                        password = ""
                        confirm.value = ""
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
    val scope = rememberCoroutineScope()

    val mismatch = confirm.value.isNotEmpty() && newPassword != confirm.value
    val valid = currentPassword.isNotEmpty() &&
        newPassword.length >= MasterPasswordManager.MIN_PASSWORD_LENGTH &&
        newPassword == confirm.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Master Password") },
        text = {
            Column {
                TextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error.value = "" },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error.value = "" },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = confirm.value,
                    onValueChange = { confirm.value = it; error.value = "" },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text("Passwords do not match") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.value.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error.value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val oldChars = currentPassword.toCharArray()
                        val verified = withContext(Dispatchers.Default) {
                            app.masterPasswordManager.verifyPassword(oldChars)
                        }
                        if (!verified) {
                            error.value = "Current password is incorrect"
                            currentPassword = ""
                            return@launch
                        }

                        val newChars = newPassword.toCharArray()
                        // Warm caches with old key BEFORE swapping keys.
                        val authMode = app.publicStore.getAuthMode()
                        val secretStore = if (authMode == AuthMode.NONE) {
                            app.getSecretStore(authMode).also { it.readData() }
                        } else null

                        val success = withContext(Dispatchers.Default) {
                            app.masterPasswordManager.changePassword(newChars)
                        }
                        if (success) {
                            app.publicStore.rewrite()
                            secretStore?.rewrite()
                            newChars.fill('\u0000')
                            currentPassword = ""
                            newPassword = ""
                            confirm.value = ""
                            onDismiss()
                        } else {
                            newChars.fill('\u0000')
                            error.value = "Failed to change password"
                        }
                    }
                },
                enabled = valid
            ) { Text("Change Password") }
        }
    )
}
