package io.raccoonwallet.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.TransactionRecord
import io.raccoonwallet.app.core.model.TxStatus

@Composable
fun TransactionCard(
    tx: TransactionRecord,
    showChainBadge: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when (tx.status) {
                    TxStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
                    TxStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                    TxStatus.FAILED -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "To: ${tx.to.truncateAddress()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val subtitle = if (showChainBadge) {
                    val chainName = ChainRegistry.byChainId(tx.chainId)?.name ?: "Unknown"
                    "${tx.status.name} · $chainName"
                } else {
                    tx.status.name
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(tx.value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
