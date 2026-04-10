package io.raccoonwallet.app.core.storage

import kotlinx.serialization.Serializable

/**
 * Serializable data classes for the two encrypted JSON stores.
 * These mirror the domain models but are storage-specific (Base64-encoded bytes, etc.)
 */

@Serializable
data class PublicStoreData(
    val appMode: String? = null,
    val dkgComplete: Boolean = false,
    val accounts: List<StoredAccount> = emptyList(),
    val activeAccountIndex: Int = 0,
    val activeChainId: Long = 1L,
    val txHistory: List<StoredTxRecord> = emptyList(),
    val balanceCache: Map<String, String> = emptyMap(),
    val tokenBalanceCache: Map<String, String> = emptyMap(),
    val authMode: String? = null
)

@Serializable
data class StoredAccount(
    val index: Int,
    val address: String,
    val publicKeyCompressed: String,
    val label: String = ""
)

@Serializable
data class StoredTxRecord(
    val hash: String,
    val chainId: Long,
    val from: String,
    val to: String,
    val value: String,
    val timestamp: Long,
    val status: String
)

@Serializable
data class SecretStoreData(
    val paillierN: String? = null,
    val paillierG: String? = null,
    val paillierLambda: String? = null,
    val paillierMu: String? = null,
    val signerPaillierN: String? = null,
    val signerPaillierG: String? = null,
    val shares: List<StoredKeyShare> = emptyList()
)

@Serializable
data class StoredKeyShare(
    val accountIndex: Int,
    val share: String? = null,
    val jointPublicKey: String? = null,
    val ckey: String? = null
)
