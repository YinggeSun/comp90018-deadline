package com.comp90018.deadline.feature.game.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing

const val STRESS_INDICATOR_TAG = "stress_indicator"

/**
 * Stress bar. Switches to the error colour and shows a warning once the
 * game reports High Stress, and pulses once it reaches Maximum Stress.
 */
@Composable
fun StressIndicator(
    stress: Int,
    stressMaximum: Int,
    isHighStress: Boolean,
    modifier: Modifier = Modifier,
    isMaxStress: Boolean = false,
) {
    val progress = if (stressMaximum > 0) stress.toFloat() / stressMaximum else 0f
    val baseColor =
        if (isHighStress || isMaxStress) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    // The pulse only runs at Maximum Stress, so a calm gauge does not animate every frame.
    val color = if (isMaxStress) baseColor.copy(alpha = pulseAlpha()) else baseColor
    val description = stringResource(R.string.game_stress_description, stress, stressMaximum)
    val warning =
        stringResource(if (isMaxStress) R.string.game_stress_max else R.string.game_stress_high)
    val showWarning = isHighStress || isMaxStress

    Column(
        modifier =
            modifier
                .testTag(STRESS_INDICATOR_TAG)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (showWarning) stateDescription = warning
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                text = stringResource(R.string.game_stress_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (showWarning) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun pulseAlpha(): Float {
    val alpha by rememberInfiniteTransition(label = "stressPulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
        label = "stressPulseAlpha",
    )
    return alpha
}

@Preview(showBackground = true)
@Composable
private fun StressIndicatorPreview() {
    DeadlineTheme {
        Surface {
            Column(modifier = Modifier.padding(Spacing.large)) {
                StressIndicator(stress = 30, stressMaximum = 100, isHighStress = false)
                StressIndicator(stress = 85, stressMaximum = 100, isHighStress = true)
                StressIndicator(stress = 100, stressMaximum = 100, isHighStress = true, isMaxStress = true)
            }
        }
    }
}
