package com.comp90018.deadline.feature.game

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * UI-only transform for issue #27.
 * Apply to each rendered board layer from issue #30.
 * It changes only rendering alpha/offset and never board state.
 */
fun Modifier.tiltPeek(
    layerIndex: Int,
    topLayerIndex: Int,
    peekAmount: Float,
): Modifier {
    val amount = peekAmount.coerceIn(0f, 1f)
    val depth = (topLayerIndex - layerIndex).coerceAtLeast(0)

    return graphicsLayer {
        // Lower/covered layers become more visible as the user tilts.
        alpha = if (depth == 0) 1f else (0.35f + 0.65f * amount).coerceIn(0f, 1f)

        // Separate layers visually while peeking; values are pixels in graphicsLayer.
        translationY = depth * 18f * amount
        translationX = depth * 8f * amount
    }
}
