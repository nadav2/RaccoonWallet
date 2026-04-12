package io.raccoonwallet.app.feature.signer.confirm

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.raccoonwallet.app.core.crypto.Hex
import io.raccoonwallet.app.core.model.ChainRegistry
import io.raccoonwallet.app.core.model.TokenRegistry
import io.raccoonwallet.app.core.network.Erc20Abi
import io.raccoonwallet.app.ui.components.FlowErrorCard
import io.raccoonwallet.app.ui.components.RaccoonWalletTopBar
import io.raccoonwallet.app.ui.components.QrFrameDisplay
import io.raccoonwallet.app.ui.components.SetupStepIndicator
import io.raccoonwallet.app.ui.components.truncateAddress
import java.math.BigInteger

@Composable
fun SignerConfirmScreen(
    onDone: () -> Unit,
    viewModel: SignerConfirmViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val activity = LocalActivity.current as FragmentActivity

    Scaffold(
        topBar = {
            RaccoonWalletTopBar(
                title = "Sign Request",
                onBack = if (state is SignerConfirmState.Done || state is SignerConfirmState.ResponseReady) null
                else ({ viewModel.reject(); onDone() })
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Step indicator: Review -> Compute -> Send
            val step = signerConfirmStep(state)
            if (step != null) {
                SetupStepIndicator(currentStep = step, totalSteps = 3)
                Spacer(modifier = Modifier.height(16.dp))
            }

            when (val s = state) {
                is SignerConfirmState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading request...")
                }

                is SignerConfirmState.ShowingRequest -> {
                    val req = s.request
                    val chain = remember(req.chainId) { ChainRegistry.byChainId(req.chainId) ?: ChainRegistry.ETHEREUM }
                    val (toAddress, valueFormatted) = remember(req.txData, req.to, req.chainId, req.valueWei) {
                        val decoded = if (req.txData != "0x") Erc20Abi.decodeTransfer(req.txData) else null
                        val token = if (decoded != null) TokenRegistry.findByAddress(req.chainId, req.to) else null
                        val to = decoded?.first ?: req.to
                        val value = if (decoded != null && token != null) {
                            "${Hex.weiToEther(decoded.second, token.decimals)} ${token.symbol}"
                        } else if (decoded != null) {
                            "Token Transfer"
                        } else {
                            "${Hex.weiToEther(BigInteger(req.valueWei, 10))} ${chain.symbol}"
                        }
                        to to value
                    }
                    val feeFormatted = remember(req.gasLimit, req.maxFeePerGas) {
                        val feeWei = BigInteger.valueOf(req.gasLimit).multiply(BigInteger(req.maxFeePerGas, 10))
                        "~${Hex.weiToEther(feeWei)} ${chain.symbol}"
                    }

                    Text("Sign Transaction?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (s.fingerprint != null) {
                                DetailRow("Connection", s.fingerprint)
                            }
                            DetailRow("Chain", chain.name)
                            DetailRow("To", toAddress.truncateAddress())
                            DetailRow("Value", valueFormatted)
                            DetailRow("Max Fee", feeFormatted)
                            if (req.txData != "0x") {
                                DetailRow("Data", "Contract call")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.reject(); onDone() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Reject") }
                        Button(
                            onClick = { viewModel.approve(activity) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Approve") }
                    }
                }

                is SignerConfirmState.Computing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Computing partial signature...")
                }

                is SignerConfirmState.ResponseReady -> {
                    Icon(
                        imageVector = Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Signature Ready", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Hold near Vault device to send signature",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(onClick = onDone) { Text("Done") }
                }

                is SignerConfirmState.DisplayingQr -> {
                    Text("Show to Vault", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    QrFrameDisplay(frames = s.frames)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onDone) { Text("Done") }
                }

                is SignerConfirmState.Done -> {
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(15000)
                        onDone()
                    }
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Signature Sent", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDone) { Text("Back") }
                }

                is SignerConfirmState.Failed -> {
                    FlowErrorCard(
                        error = s.error,
                        onStartOver = onDone
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Map signer confirm state to step number.
 * 3 steps: Review -> Compute -> Send
 */
private fun signerConfirmStep(state: SignerConfirmState): Int? = when (state) {
    is SignerConfirmState.Loading, is SignerConfirmState.ShowingRequest -> 1
    is SignerConfirmState.Computing -> 2
    is SignerConfirmState.ResponseReady, is SignerConfirmState.DisplayingQr,
    is SignerConfirmState.Done -> 3
    else -> null
}
