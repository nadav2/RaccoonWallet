package io.raccoonwallet.app.ui.components

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.raccoonwallet.app.R
import kotlinx.coroutines.launch

private val raccoonFacts = listOf(
    "Raccoons can rotate their hind feet 180\u00B0",
    "Raccoons wash their food before eating",
    "A group of raccoons is called a gaze",
    "Raccoons have 5 toes on each paw, like humans",
    "Raccoons can run up to 15 mph",
    "Baby raccoons are called kits",
    "Raccoons have been around for 40,000 years",
    "Raccoons can remember solutions to tasks for 3 years",
    "Raccoons have 4x more sensory cells in their paws than most mammals",
    "Raccoons can unlock complex locks and open jars",
)

@Composable
fun RaccoonEasterEgg() {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(tapCount) {
        if (tapCount == 0) return@LaunchedEffect
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                initialVelocity = 4f,
            )
        }
        launch {
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 300
                    (-12f) at 50
                    12f at 100
                    (-8f) at 175
                    8f at 225
                    0f at 300
                },
            )
        }
    }

    Image(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                tapCount++
                Toast
                    .makeText(context, raccoonFacts.random(), Toast.LENGTH_SHORT)
                    .show()
            },
    )
}
