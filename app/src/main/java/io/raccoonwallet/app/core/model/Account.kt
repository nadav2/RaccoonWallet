package io.raccoonwallet.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val index: Int,
    val address: String,
    val publicKeyCompressed: String,
    val label: String = "Account ${index + 1}"
)
