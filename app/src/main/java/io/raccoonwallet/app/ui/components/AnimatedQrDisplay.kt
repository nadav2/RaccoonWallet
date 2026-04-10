package io.raccoonwallet.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Displays one or more QR code bitmaps in a cycling animation.
 * For single-frame payloads, shows a static QR code.
 * For multi-frame, cycles at [frameDelayMs] intervals.
 */
@Composable
fun AnimatedQrDisplay(
    frames: List<Bitmap>,
    modifier: Modifier = Modifier,
    frameDelayMs: Long = 500,
    size: Int = 300
) {
    if (frames.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    if (frames.size > 1) {
        LaunchedEffect(frames.size) {
            while (true) {
                delay(frameDelayMs)
                currentIndex = (currentIndex + 1) % frames.size
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = modifier.size(size.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = frames[currentIndex].asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.size(size.dp)
            )
        }

        if (frames.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / frames.size }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Frame ${currentIndex + 1} of ${frames.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
