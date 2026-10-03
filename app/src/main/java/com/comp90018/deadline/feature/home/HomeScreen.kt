package com.comp90018.deadline.feature.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.CenteredScrollableColumn
import com.comp90018.deadline.core.ui.components.PrimaryButton
import com.comp90018.deadline.core.ui.components.SecondaryButton

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onContinue: (levelId: String) -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeContent(
        uiState = uiState,
        onPlay = onPlay,
        onContinue = onContinue,
        onLeaderboard = onLeaderboard,
        onSettings = onSettings
    )
}

/**
 * Stateless body of the Home screen. When there is a level to continue,
 * Continue becomes the main action and Play moves to a secondary button.
 */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onPlay: () -> Unit,
    onContinue: (levelId: String) -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    CenteredScrollableColumn(modifier = modifier) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.extraLarge))

        val continueLevelId = uiState.continueLevelId
        if (continueLevelId != null) {
            PrimaryButton(
                text = stringResource(R.string.home_continue),
                onClick = { onContinue(continueLevelId) }
            )
            SecondaryButton(text = stringResource(R.string.home_play), onClick = onPlay)
        } else {
            PrimaryButton(text = stringResource(R.string.home_play), onClick = onPlay)
        }
        SecondaryButton(text = stringResource(R.string.home_leaderboard), onClick = onLeaderboard)
        SecondaryButton(text = stringResource(R.string.home_settings), onClick = onSettings)
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreview() {
    DeadlineTheme {
        Surface {
            HomeContent(HomeUiState(), onPlay = {}, onContinue = {}, onLeaderboard = {}, onSettings = {})
        }
    }
}

@Preview(name = "With Continue", showBackground = true)
@Composable
private fun HomeContinuePreview() {
    DeadlineTheme {
        Surface {
            HomeContent(HomeUiState(continueLevelId = "level_2"), onPlay = {}, onContinue = {}, onLeaderboard = {}, onSettings = {})
        }
    }
}
