package io.raccoonwallet.app.core.model

sealed class DkgState {
    data object Idle : DkgState()
    data object ChoosingSeedGeneration : DkgState()
    data class CollectingEntropy(
        val tapCount: Int = 0,
        val progress: Float = 0f,
        val isReady: Boolean = false
    ) : DkgState()
    data object GeneratingSeed : DkgState()
    data class ShowingSeed(val words: List<String>) : DkgState() {
        override fun toString() = "ShowingSeed(words=[REDACTED, ${words.size} words])"
    }
    data object ImportingSeed : DkgState()
    data object SeedConfirmed : DkgState()
    data object GeneratingPaillier : DkgState()
    data object SplittingKeys : DkgState()
    data object ChoosingBiometric : DkgState()
    data object ChoosingTransport : DkgState()

    // NFC flow
    data object AwaitingNfcTap : DkgState()
    data class ReceivingNfc(val progress: Float = 0f) : DkgState()

    // QR flow
    data class DisplayingQr(val frames: List<String>) : DkgState()
    data class ScanningQr(val progress: Float = 0f) : DkgState()

    // Signer: waiting for data from Vault
    data object WaitingForVault : DkgState()
    data class SignerReceived(val accountCount: Int) : DkgState()
    data class DisplayingAck(val frames: List<String>) : DkgState()

    data object AwaitingBiometric : DkgState()
    data object StoringKeys : DkgState()
    data object Complete : DkgState()
    data class Failed(val error: FlowError) : DkgState()
}

sealed class SignState {
    data object Idle : SignState()
    data object BuildingTransaction : SignState()
    data object ChoosingTransport : SignState()
    data object AwaitingTap1 : SignState()
    data class DisplayingQr(val frames: List<String>) : SignState()
    data object RequestDelivered : SignState()
    data class WaitingForApproval(val fingerprint: String? = null) : SignState()
    data object AwaitingTap2 : SignState()
    data class ScanningQr(val progress: Float = 0f) : SignState()
    data object Finalizing : SignState()
    data class Complete(val txHash: String) : SignState()
    data class Rejected(val reason: String) : SignState()
    data class Failed(val error: FlowError) : SignState()
}
