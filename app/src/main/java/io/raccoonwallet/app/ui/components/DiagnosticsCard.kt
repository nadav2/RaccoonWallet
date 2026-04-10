package io.raccoonwallet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.core.model.DiagnosticsData

/**
 * Card showing wallet health and configuration for settings screens.
 * Helps users and support diagnose issues without exposing secrets.
 */
@Composable
fun DiagnosticsCard(
    data: DiagnosticsData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleSmall)

            DiagRow("Mode", data.mode)
            DiagRow("Accounts", "${data.accountCount}")
            DiagRow("DKG", if (data.dkgComplete) "Complete" else "Incomplete")
            DiagRow("Auth mode", data.authModeDisplay)
            DiagRow("StrongBox", if (data.strongBox) "Active" else "Not available")
            DiagRow("NFC", if (data.nfcAvailable) "Available" else "Not available")
            DiagRow("Public store", storeStatus(data.publicStoreExists, data.publicStoreSize))
            DiagRow("Secret store", storeStatus(data.secretStoreExists, data.secretStoreSize))
            DiagRow("Public key", if (data.publicKeyOk) "OK" else "Missing")
            DiagRow("Secret key", if (data.secretKeyOk) "OK" else "Missing")
            DiagRow("Version", "v${data.versionName}")
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun storeStatus(exists: Boolean, size: Long): String = when {
    !exists -> "Missing"
    size == 0L -> "Empty"
    size < 1024 -> "OK ($size B)"
    else -> "OK (${size / 1024} KB)"
}
