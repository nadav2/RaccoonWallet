package io.raccoonwallet.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Shows [message] normally, switching to [warningMessage] in error color
 * after [warningAfterSeconds] of continuous display.
 */
@Composable
fun NfcWaitHint(
    modifier: Modifier = Modifier,
    message: String,
    warningMessage: String,
    warningAfterSeconds: Int = 15,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    val showWarning by remember { derivedStateOf { elapsed >= warningAfterSeconds } }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsed++
        }
    }

    Text(
        text = if (showWarning) warningMessage else message,
        style = MaterialTheme.typography.bodySmall,
        color = if (showWarning) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
