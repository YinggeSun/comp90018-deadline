package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.ErrorContent
import com.comp90018.deadline.core.ui.components.LoadingContent
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.domain.level.model.SemesterLevel

/**
 * The Game destination: prepares a new board for [levelId] off the main thread, showing a
 * loading state meanwhile, then shows the Game screen for it.
 */
@Composable
fun GameRoute(
    levelId: String,
    levelSource: LevelSource,
    onGameFinished: (GameOutcome) -> Unit,
    onBack: () -> Unit,
) {
    val loader: GameLoadViewModel = viewModel(factory = GameLoadViewModel.factory(levelId, levelSource))
    val state by loader.state.collectAsState()

    when (val current = state) {
        is GameLoadState.Ready ->
            GameScreen(
                levelId = levelId,
                onGameFinished = onGameFinished,
                onBack = onBack,
                viewModel = viewModel(factory = GameViewModel.factory(current.level)),
            )

        GameLoadState.Loading ->
            GameLoadFrame(levelId = levelId, onBack = onBack) {
                LoadingContent(message = stringResource(R.string.game_level_preparing))
            }

        GameLoadState.Failed ->
            GameLoadFrame(levelId = levelId, onBack = onBack) {
                ErrorContent(
                    message = stringResource(R.string.game_level_load_failed),
                    onRetry = loader::retry,
                )
            }
    }
}

/** The Game screen's top bar above a loading or error body, so Back is always available. */
@Composable
private fun GameLoadFrame(
    levelId: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TertiaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text(
                text = SemesterLevel.fromId(levelId)?.name.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        content()
    }
}
