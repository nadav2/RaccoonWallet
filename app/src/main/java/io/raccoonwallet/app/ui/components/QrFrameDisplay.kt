package io.raccoonwallet.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.raccoonwallet.app.core.transport.qr.QrEncoder

/**
 * Converts QR frame strings to bitmaps and displays them with AnimatedQrDisplay.
 * Shared by VaultSignScreen and SignerConfirmScreen.
 */
@Composable
fun QrFrameDisplay(frames: List<String>) {
    val encoder = remember { QrEncoder() }
    val bitmaps = remember(frames) { frames.map { encoder.frameToBitmap(it) } }
    AnimatedQrDisplay(frames = bitmaps)
}
