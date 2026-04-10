package io.raccoonwallet.app.core.model

import kotlinx.serialization.Serializable

@androidx.annotation.Keep
@Serializable
enum class TransportMode { NFC, QR }
