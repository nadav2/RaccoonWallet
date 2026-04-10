package io.raccoonwallet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.core.model.FlowError

/**
 * Shared error display for DKG, signing, and signer confirmation flows.
 * Shows the error message, contextual guidance based on error type, and
 * action buttons appropriate for the error's suggested recovery action.
 */
@Composable
fun FlowErrorCard(
    modifier: Modifier = Modifier,
    error: FlowError,
    onRetry: (() -> Unit)? = null,
    onStartOver: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error.userMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = guidanceText(error.action),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (error.action) {
            FlowError.Action.RETRY, FlowError.Action.RETRY_TAP -> {
                if (onRetry != null) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try again")
                    }
                }
                if (onStartOver != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onStartOver,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start over")
                    }
                }
            }
            FlowError.Action.RESTART_PAIRING -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (onRetry != null) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry")
                        }
                    }
                    if (onStartOver != null) {
                        Button(
                            onClick = onStartOver,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start over")
                        }
                    }
                }
            }
            FlowError.Action.RESTART_SETUP, FlowError.Action.RESET_WALLET -> {
                if (onStartOver != null) {
                    Button(
                        onClick = onStartOver,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (error.action == FlowError.Action.RESET_WALLET) "Reset wallet" else "Start over")
                    }
                }
            }
        }
    }
}

private fun guidanceText(action: FlowError.Action): String = when (action) {
    FlowError.Action.RETRY -> "You can try again."
    FlowError.Action.RETRY_TAP -> "Re-tap or re-scan to try again."
    FlowError.Action.RESTART_PAIRING -> "You may need to restart the pairing process."
    FlowError.Action.RESTART_SETUP -> "The process needs to be restarted."
    FlowError.Action.RESET_WALLET -> "Wallet data may be corrupted and needs to be reset."
}
