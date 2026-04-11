package io.raccoonwallet.app.core.model

/**
 * Categorized error types for DKG, signing, and transport flows.
 * Each variant carries a user-facing message and a suggested recovery action,
 * enabling the UI to show contextual guidance instead of raw exception text.
 */
sealed class FlowError {
    abstract val userMessage: String
    abstract val action: Action

    enum class Action {
        /** Generic retry (e.g., re-attempt computation) */
        RETRY,
        /** Re-tap NFC or re-scan QR */
        RETRY_TAP,
        /** Go back to transport selection */
        RESTART_PAIRING,
        /** Restart the entire flow (DKG or signing) */
        RESTART_SETUP,
        /** Wallet data is corrupted — full reset needed */
        RESET_WALLET
    }

    // ── Transport ──

    data class ConnectionFailed(val detail: String? = null) : FlowError() {
        override val userMessage = "Could not connect to the other device" +
            (detail?.let { " — $it" } ?: "") + ". Make sure NFC is enabled on both devices."
        override val action = Action.RETRY_TAP
    }

    data class TransferInterrupted(val detail: String? = null) : FlowError() {
        override val userMessage = "Transfer was interrupted" +
            (detail?.let { " — $it" } ?: "") + ". Keep devices together and try again."
        override val action = Action.RETRY_TAP
    }

    data class Timeout(val phase: String) : FlowError() {
        override val userMessage = "Timed out during $phase. Check that both devices are ready and try again."
        override val action = Action.RETRY_TAP
    }

    data class DeviceNotReady(val detail: String? = null) : FlowError() {
        override val userMessage = "The other device isn't ready" +
            (detail?.let { " — $it" } ?: "") + ". Make sure it's on the correct screen."
        override val action = Action.RETRY_TAP
    }

    data class QrScanFailed(val detail: String) : FlowError() {
        override val userMessage = "QR scan error: $detail"
        override val action = Action.RETRY_TAP
    }

    data class ProtocolMismatch(val detail: String? = null) : FlowError() {
        override val userMessage = "Unexpected response from the other device" +
            (detail?.let { " — $it" } ?: "") + ". Both devices may need to start over."
        override val action = Action.RESTART_PAIRING
    }

    // ── Storage ──

    data class StorageFailed(val detail: String) : FlowError() {
        override val userMessage = "Failed to save data: $detail"
        override val action = Action.RESTART_SETUP
    }

    // ── Auth ──

    data class BiometricDenied(val detail: String? = null) : FlowError() {
        override val userMessage = detail ?: "Authentication required to access key shares."
        override val action = Action.RETRY
    }

    // ── Crypto / signing ──

    data class CryptoFailed(val detail: String) : FlowError() {
        override val userMessage = detail
        override val action = Action.RESTART_SETUP
    }

    data class KeyShareMissing(val detail: String) : FlowError() {
        override val userMessage = "Required key data not found: $detail. Wallet may need to be re-paired."
        override val action = Action.RESET_WALLET
    }

    data class BroadcastFailed(val detail: String) : FlowError() {
        override val userMessage = "Transaction broadcast failed: $detail. Your funds are safe."
        override val action = Action.RETRY
    }
}
