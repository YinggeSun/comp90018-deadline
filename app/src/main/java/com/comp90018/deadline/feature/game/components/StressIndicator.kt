package com.comp90018.deadline.feature.game.components

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
 * game reports High Stress.
 */
@Composable
fun StressIndicator(
    stress: Int,
    stressMaximum: Int,
    isHighStress: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = if (stressMaximum > 0) stress.toFloat() / stressMaximum else 0f
    val color = if (isHighStress) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val description = stringResource(R.string.game_stress_description, stress, stressMaximum)
    val warning = stringResource(R.string.game_stress_high)

    Column(
        modifier =
            modifier
                .testTag(STRESS_INDICATOR_TAG)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (isHighStress) stateDescription = warning
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                text = stringResource(R.string.game_stress_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (isHighStress) {
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

@Preview(showBackground = true)
@Composable
private fun StressIndicatorPreview() {
    DeadlineTheme {
        Surface {
            Column(modifier = Modifier.padding(Spacing.large)) {
                StressIndicator(stress = 30, stressMaximum = 100, isHighStress = false)
                StressIndicator(stress = 85, stressMaximum = 100, isHighStress = true)
            }
        }
    }
}
