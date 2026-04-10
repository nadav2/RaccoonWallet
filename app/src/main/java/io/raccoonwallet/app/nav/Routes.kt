package io.raccoonwallet.app.nav

import io.raccoonwallet.app.core.model.TransportMode
import kotlinx.serialization.Serializable

// ── Shared Routes ──
@Serializable data class ModeSelect(val showResetNotice: Boolean = false)
@Serializable data class SetupDkg(val isVault: Boolean)

// ── Vault Routes ──
@Serializable data object VaultDashboard
@Serializable data class VaultSend(val chainId: Long, val accountIndex: Int)
@Serializable data class VaultReceive(val accountIndex: Int)
@Serializable data class VaultSign(
    val chainId: Long,
    val accountIndex: Int,
    val to: String,
    val valueWei: String,
    val data: String = "0x",
    val nonce: Long,
    val gasLimit: Long,
    val maxFeePerGas: String,
    val maxPriorityFeePerGas: String
)
@Serializable data object TransactionHistory
@Serializable data object VaultSettings

// ── Signer Routes ──
@Serializable data object SignerIdle
@Serializable data class SignerConfirm(val sessionId: String, val transportMode: TransportMode)
@Serializable data object SignerSettings
