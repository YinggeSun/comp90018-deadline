package com.comp90018.deadline.feature.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.util.TimeFormatter

const val GAME_TIMER_TAG = "game_timer"

/** Status row above the board: completion timer and stress bar. */
@Composable
fun GameHud(
    elapsedSeconds: Long,
    stress: Int,
    stressMaximum: Int,
    isHighStress: Boolean,
    modifier: Modifier = Modifier,
    isMaxStress: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val time = TimeFormatter.formatSeconds(elapsedSeconds)
        val timeDescription = stringResource(R.string.game_timer_description, time)
        Text(
            text = time,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .testTag(GAME_TIMER_TAG)
                    .clearAndSetSemantics { contentDescription = timeDescription },
        )
        StressIndicator(
            stress = stress,
            stressMaximum = stressMaximum,
            isHighStress = isHighStress,
            isMaxStress = isMaxStress,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameHudPreview() {
    DeadlineTheme {
        Surface {
            GameHud(
                elapsedSeconds = 125,
                stress = 40,
                stressMaximum = 100,
                isHighStress = false,
                modifier = Modifier.padding(Spacing.large),
            )
        }
    }
}
