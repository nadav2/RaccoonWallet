package io.raccoonwallet.app.core.model

data class DiagnosticsData(
    val versionName: String,
    val mode: String,
    val accountCount: Int,
    val dkgComplete: Boolean,
    val publicStoreExists: Boolean,
    val publicStoreSize: Long,
    val secretStoreExists: Boolean,
    val secretStoreSize: Long,
    val publicKeyOk: Boolean,
    val secretKeyOk: Boolean,
    val authModeDisplay: String,
    val strongBox: Boolean,
    val nfcAvailable: Boolean
)
