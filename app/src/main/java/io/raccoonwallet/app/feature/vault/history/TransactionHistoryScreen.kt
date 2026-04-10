package io.raccoonwallet.app.feature.vault.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.TxStatus
import io.raccoonwallet.app.ui.components.RaccoonWalletTopBar
import io.raccoonwallet.app.ui.components.TransactionCard
import io.raccoonwallet.app.ui.components.TransactionDetailSheet
import io.raccoonwallet.app.ui.components.truncateAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    viewModel: TransactionHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val filter = uiState.filter

    val showStartDatePicker = remember { mutableStateOf(false) }
    val showEndDatePicker = remember { mutableStateOf(false) }

    val showChainSheet = remember { mutableStateOf(false) }
    val showAccountSheet = remember { mutableStateOf(false) }

    Scaffold(
        topBar = { RaccoonWalletTopBar(title = "Transaction History", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chain & Account selectors
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chainName = filter.chainFilter
                        ?.let { ChainRegistry.byChainId(it)?.name }
                        ?: "All Chains"
                    AssistChip(
                        onClick = { showChainSheet.value = true },
                        label = { Text(chainName) }
                    )
                    if (uiState.allAccounts.size > 1) {
                        val account = uiState.allAccounts.getOrNull(uiState.activeAccountIndex)
                        AssistChip(
                            onClick = { showAccountSheet.value = true },
                            label = { Text(account?.label ?: "Account ${uiState.activeAccountIndex + 1}") }
                        )
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = filter.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search by hash or address") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Status filter chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter.statusFilter == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("All") }
                    )
                    TxStatus.entries.forEach { status ->
                        FilterChip(
                            selected = filter.statusFilter == status,
                            onClick = { viewModel.setStatusFilter(status) },
                            label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }


            // Date range
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showStartDatePicker.value = true }) {
                            Text(filter.dateRangeStart?.let { formatDate(it) } ?: "From")
                        }
                        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { showEndDatePicker.value = true }) {
                            Text(filter.dateRangeEnd?.let { formatDate(it) } ?: "To")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (filter.dateRangeStart != null || filter.dateRangeEnd != null) {
                            IconButton(
                                onClick = { viewModel.setDateRange(null, null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear dates", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Transaction list
            if (uiState.filteredTransactions.isEmpty() && !uiState.isLoading) {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = if (filter == TxFilterState()) "No transactions yet" else "No transactions match your filters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.filteredTransactions, key = { it.hash }) { tx ->
                    TransactionCard(
                        tx = tx,
                        showChainBadge = filter.chainFilter == null && uiState.availableChains.size > 1,
                        onClick = { viewModel.selectTransaction(tx) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Detail sheet
    uiState.selectedTransaction?.let { tx ->
        TransactionDetailSheet(
            tx = tx,
            onDismiss = { viewModel.selectTransaction(null) }
        )
    }

    // Date picker dialogs
    if (showStartDatePicker.value) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filter.dateRangeStart)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    showStartDatePicker.value = false
                    viewModel.setDateRange(state.selectedDateMillis, filter.dateRangeEnd)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker.value = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    if (showEndDatePicker.value) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filter.dateRangeEnd)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker.value = false },
            confirmButton = {
                TextButton(onClick = {
                    showEndDatePicker.value = false
                    // Add end-of-day to make the range inclusive
                    val endMillis = state.selectedDateMillis?.let { it + 86_399_999L }
                    viewModel.setDateRange(filter.dateRangeStart, endMillis)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker.value = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    // Chain selector sheet
    if (showChainSheet.value) {
        ModalBottomSheet(
            onDismissRequest = { showChainSheet.value = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Chain", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    onClick = {
                        viewModel.setChainFilter(null)
                        showChainSheet.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (filter.chainFilter == null)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        "All Chains",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                uiState.allChains.forEach { chain ->
                    Card(
                        onClick = {
                            viewModel.setChainFilter(chain.chainId)
                            showChainSheet.value = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (filter.chainFilter == chain.chainId)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            "${chain.name} (${chain.symbol})",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Account selector sheet
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
                        colors = CardDefaults.cardColors(
                            containerColor = if (account.index == uiState.activeAccountIndex)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
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
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
