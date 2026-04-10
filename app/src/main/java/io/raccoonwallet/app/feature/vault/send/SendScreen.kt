package io.raccoonwallet.app.feature.vault.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.nav.VaultSign
import io.raccoonwallet.app.ui.components.RaccoonWalletTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onSignRequired: (VaultSign) -> Unit,
    onBack: () -> Unit,
    viewModel: SendViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            RaccoonWalletTopBar(title = "Send", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Token selector
            var tokenExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = tokenExpanded,
                onExpandedChange = { tokenExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedToken?.symbol ?: uiState.chainSymbol,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Token") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tokenExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = tokenExpanded,
                    onDismissRequest = { tokenExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("${uiState.chainSymbol} (Native)") },
                        onClick = {
                            viewModel.selectToken(null)
                            tokenExpanded = false
                        }
                    )
                    uiState.availableTokens.forEach { token ->
                        DropdownMenuItem(
                            text = { Text("${token.symbol} — ${token.name}") },
                            onClick = {
                                viewModel.selectToken(token)
                                tokenExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Balance display
            Text(
                text = if (uiState.selectedToken != null) "Balance: ${uiState.tokenBalance}"
                else "Balance: ${uiState.nativeBalanceFormatted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // To address
            OutlinedTextField(
                value = uiState.toAddress,
                onValueChange = { viewModel.setToAddress(it) },
                label = { Text("To Address") },
                placeholder = { Text("0x...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.addressError != null,
                supportingText = uiState.addressError?.let { { Text(it) } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { viewModel.setAmount(it) },
                label = { Text("Amount") },
                placeholder = { Text("0.0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                suffix = { Text(uiState.selectedToken?.symbol ?: uiState.chainSymbol) },
                isError = uiState.amountError != null,
                supportingText = uiState.amountError?.let { { Text(it) } }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Fee Section ──
            Text("Network Fee", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            // Fee tier chips — always visible once fees are loaded
            val fee = uiState.fee
            if (fee.slowFee != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = fee.selectedTier == FeeTier.SLOW,
                        onClick = { viewModel.selectFeeTier(FeeTier.SLOW) },
                        label = { Text("Slow") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = fee.selectedTier == FeeTier.NORMAL,
                        onClick = { viewModel.selectFeeTier(FeeTier.NORMAL) },
                        label = { Text("Normal") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = fee.selectedTier == FeeTier.FAST,
                        onClick = { viewModel.selectFeeTier(FeeTier.FAST) },
                        label = { Text("Fast") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom fee toggle
                var showCustom by remember { mutableStateOf(fee.selectedTier == FeeTier.CUSTOM) }
                TextButton(onClick = {
                    showCustom = !showCustom
                    if (showCustom) {
                        // Pre-fill custom fields from current normal tier
                        val normal = fee.normalFee
                        if (normal != null && fee.customMaxFeeGwei.isEmpty()) {
                            viewModel.setCustomMaxFeeGwei(
                                SendViewModel.weiToGwei(normal.maxFeePerGas)
                            )
                            viewModel.setCustomPriorityFeeGwei(
                                SendViewModel.weiToGwei(normal.maxPriorityFeePerGas)
                            )
                            viewModel.setCustomGasLimit(fee.gasLimit.toString())
                        }
                    }
                }) {
                    Text(if (showCustom) "Hide custom fees" else "Set custom fees")
                }

                AnimatedVisibility(visible = showCustom) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            OutlinedTextField(
                                value = fee.customMaxFeeGwei,
                                onValueChange = { viewModel.setCustomMaxFeeGwei(it) },
                                label = { Text("Max Fee (Gwei)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = fee.customPriorityFeeGwei,
                                onValueChange = { viewModel.setCustomPriorityFeeGwei(it) },
                                label = { Text("Priority Fee (Gwei)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = fee.customGasLimit,
                                onValueChange = { viewModel.setCustomGasLimit(it) },
                                label = { Text("Gas Limit") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show active fee
                if (fee.estimatedFee.isNotEmpty()) {
                    Text(
                        text = "Estimated fee: ${fee.estimatedFee}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (fee.isEstimating) {
                Row {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Estimating gas...", modifier = Modifier.padding(top = 8.dp))
                }
            }

            if (fee.estimatedFee.contains("failed", ignoreCase = true)) {
                Text(
                    text = fee.estimatedFee,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSignRequired(viewModel.buildSignRoute()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canSubmit && !fee.isEstimating
            ) {
                Text("Review & Sign")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

