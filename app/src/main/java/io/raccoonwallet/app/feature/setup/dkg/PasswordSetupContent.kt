package io.raccoonwallet.app.feature.setup.dkg

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.core.storage.MasterPasswordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PasswordSetupContent(
    onPasswordSet: (CharArray) -> Unit,
    onSkip: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val tooShort = password.isNotEmpty() && password.length < MasterPasswordManager.MIN_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && password != confirm
    val valid = !working &&
        password.length >= MasterPasswordManager.MIN_PASSWORD_LENGTH &&
        password == confirm

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Set Master Password",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Protect your wallet with a master password. " +
                "It will be required every time you open the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = tooShort,
            supportingText = if (tooShort) {
                { Text("Minimum ${MasterPasswordManager.MIN_PASSWORD_LENGTH} characters") }
            } else null,
            singleLine = true,
            enabled = !working,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = mismatch,
            supportingText = if (mismatch) {
                { Text("Passwords do not match") }
            } else null,
            singleLine = true,
            enabled = !working,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (working) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Setting up encryption...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(
                onClick = {
                    val chars = password.toCharArray()
                    password = ""
                    confirm = ""
                    working = true
                    scope.launch {
                        withContext(Dispatchers.Default) { onPasswordSet(chars) }
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Password")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    password = ""
                    confirm = ""
                    onSkip()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Skip")
            }
        }
    }
}
