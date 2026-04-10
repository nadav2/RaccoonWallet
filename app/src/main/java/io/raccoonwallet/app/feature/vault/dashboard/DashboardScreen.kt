package io.raccoonwallet.app.feature.vault.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.ui.components.RaccoonWalletTopBar
import io.raccoonwallet.app.ui.components.ShimmerBox
import io.raccoonwallet.app.ui.components.TransactionCard
import io.raccoonwallet.app.ui.components.TransactionDetailSheet
import io.raccoonwallet.app.ui.components.truncateAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSend: (chainId: Long, accountIndex: Int) -> Unit,
    onReceive: (accountIndex: Int) -> Unit,
    onSettings: () -> Unit,
    onViewHistory: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val showChainSheet = remember { mutableStateOf(false) }
    val showAccountSheet = remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            RaccoonWalletTopBar(
                title = "Raccoon Wallet",
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Chain & Account selector chips
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { showChainSheet.value = true },
                            label = { Text(uiState.chainName) },
                            leadingIcon = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            uiState.chainName.first().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        )
                        AssistChip(
                            onClick = { showAccountSheet.value = true },
                            label = { Text(uiState.activeAccountLabel) }
                        )
                    }
                }

                // Balance card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.chainName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (uiState.isLoading) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .height(36.dp)
                                )
                            } else {
                                Text(
                                    text = uiState.formattedBalance,
                                    style = MaterialTheme.typography.headlineLarge
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.activeAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Send/Receive buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSend(uiState.chainId, uiState.activeAccountIndex) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send")
                        }
                        OutlinedButton(
                            onClick = { onReceive(uiState.activeAccountIndex) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CallReceived, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Receive")
                        }
                    }
                }

                // Token balances
                if (uiState.tokenBalances.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tokens", style = MaterialTheme.typography.titleMedium)
                    }
                    items(uiState.tokenBalances) { tb ->
                        AnimatedVisibility(visible = true, enter = fadeIn()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(tb.token.symbol, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            tb.token.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        tb.formatted,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (tb.loadFailed) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else if (uiState.isLoading) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tokens", style = MaterialTheme.typography.titleMedium)
                    }
                    items(3) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                    }
                }

                // Transactions
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent Transactions", style = MaterialTheme.typography.titleMedium)
                }

                if (uiState.transactions.isEmpty()) {
                    item {
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(uiState.transactions.take(5)) { tx ->
                        TransactionCard(
                            tx = tx,
                            onClick = { viewModel.selectTransaction(tx) }
                        )
                    }
                }
                item {
                    androidx.compose.material3.TextButton(
                        onClick = onViewHistory,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View All Transactions")
                    }
                }

                // Bottom spacer
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Chain switcher bottom sheet
    if (showChainSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showChainSheet.value = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Chain", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                uiState.allChains.forEach { chain ->
                    Card(
                        onClick = {
                            viewModel.switchChain(chain.chainId)
                            showChainSheet.value = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = if (chain.chainId == uiState.chainId)
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else CardDefaults.cardColors()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        chain.name.first().toString(),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chain.name, style = MaterialTheme.typography.titleSmall)
                                Text(chain.symbol, style = MaterialTheme.typography.bodySmall)
                            }
                            if (chain.chainId == uiState.chainId) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Account switcher bottom sheet
    if (showAccountSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet.value = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Account", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                uiState.allAccounts.forEach { account ->
                    Card(
                        onClick = {
                            viewModel.switchAccount(account.index)
                            showAccountSheet.value = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = if (account.index == uiState.activeAccountIndex)
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        else CardDefaults.cardColors()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    account.address.truncateAddress(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (account.index == uiState.activeAccountIndex) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Transaction detail sheet
    uiState.selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            tx = tx,
            onDismiss = { viewModel.selectTransaction(null) }
        )
    }
}
