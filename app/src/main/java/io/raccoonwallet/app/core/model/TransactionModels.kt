package io.raccoonwallet.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionRecord(
    val hash: String,
    val chainId: Long,
    val from: String,
    val to: String,
    val value: String,
    val timestamp: Long,
    val status: TxStatus
)

@Serializable
enum class TxStatus { PENDING, CONFIRMED, FAILED }

@Serializable
data class TxDisplayData(
    val chainName: String,
    val fromAddress: String,
    val toAddress: String,
    val value: String,
    val data: String,
    val estimatedFee: String
)
