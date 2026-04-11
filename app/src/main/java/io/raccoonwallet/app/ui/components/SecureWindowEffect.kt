package io.raccoonwallet.app.ui.components

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val activity = LocalActivity.current

    DisposableEffect(activity, enabled) {
        val window = activity?.window
        if (window == null || !enabled) {
            onDispose { }
        } else {
            val wasSecure =
                (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
            if (!wasSecure) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            onDispose {
                if (!wasSecure) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}
