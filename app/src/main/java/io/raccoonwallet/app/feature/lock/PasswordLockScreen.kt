package io.raccoonwallet.app.feature.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UnlockResult {
    data object Success : UnlockResult()
    data class WrongPassword(val attemptsRemaining: Int?) : UnlockResult()
    data object Wipe : UnlockResult()
}

@Composable
fun PasswordLockScreen(
    onUnlock: (CharArray) -> UnlockResult,
    onWipe: () -> Unit = {}
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun attemptUnlock() {
        if (password.isEmpty() || checking) return
        val chars = password.toCharArray()
        checking = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { onUnlock(chars) }
            chars.fill('\u0000')
            // Back on Main thread — safe to mutate Compose state
            when (result) {
                is UnlockResult.Success -> checking = false
                is UnlockResult.WrongPassword -> {
                    errorMessage = if (result.attemptsRemaining != null) {
                        "Wrong password (${result.attemptsRemaining} attempts remaining)"
                    } else {
                        "Wrong password"
                    }
                    password = ""
                    checking = false
                }
                is UnlockResult.Wipe -> {
                    checking = false
                    onWipe()
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Raccoon Wallet",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your master password to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            TextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                label = { Text("Master Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { attemptUnlock() }),
                isError = errorMessage != null,
                supportingText = {
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(errorMessage ?: "")
                    }
                },
                singleLine = true,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (checking) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { attemptUnlock() },
                    enabled = password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock")
                }
            }
        }
    }
}
