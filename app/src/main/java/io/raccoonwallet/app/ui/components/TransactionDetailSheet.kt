package io.raccoonwallet.app.ui.components

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.TransactionRecord
import io.raccoonwallet.app.core.model.TxStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    tx: TransactionRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val chain = ChainRegistry.byChainId(tx.chainId)
    val formattedDate = Instant.ofEpochMilli(tx.timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Transaction Details", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(20.dp))

            // Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = when (tx.status) {
                        TxStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
                        TxStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        TxStatus.FAILED -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(tx.status.name, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("Chain", chain?.name ?: "Unknown")

            Spacer(modifier = Modifier.height(12.dp))
            CopyableDetailRow("Tx Hash", tx.hash) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("tx hash", tx.hash))
            }

            Spacer(modifier = Modifier.height(12.dp))
            CopyableDetailRow("To", tx.to) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("address", tx.to))
            }

            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("Value", tx.value)

            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("Date", formattedDate)

            Spacer(modifier = Modifier.height(24.dp))
            if (chain != null) {
                OutlinedButton(
                    onClick = {
                        val url = "${chain.explorerUrl}/tx/${tx.hash}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View on ${chain.name} Explorer")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CopyableDetailRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy",
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onCopy),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
