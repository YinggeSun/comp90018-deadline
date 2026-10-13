package com.comp90018.deadline.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Size the content starts at, as a share of its final size. */
private const val POP_IN_START_SCALE = 0.6f

/**
 * Scales and fades the content in once, when it first appears, with a small bounce.
 *
 * Only the drawing changes: the content takes its full space and can be found and tapped
 * from the first frame. With animations turned off in the system settings it appears
 * at once.
 */
@Composable
fun Modifier.popIn(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        )
    }
    return graphicsLayer {
        val scale = POP_IN_START_SCALE + (1f - POP_IN_START_SCALE) * progress.value
        scaleX = scale
        scaleY = scale
        alpha = progress.value.coerceIn(0f, 1f)
    }
}
