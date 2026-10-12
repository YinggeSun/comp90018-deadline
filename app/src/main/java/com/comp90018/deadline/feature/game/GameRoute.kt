package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.ui.CampusBackgrounds
import com.comp90018.deadline.core.ui.components.BackdropStrength
import com.comp90018.deadline.core.ui.components.ErrorContent
import com.comp90018.deadline.core.ui.components.LoadingContent
import com.comp90018.deadline.core.ui.components.PhotoBackground
import com.comp90018.deadline.core.ui.components.ScreenTopBar
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

    PhotoBackground(image = CampusBackgrounds.forLevel(levelId), strength = BackdropStrength.Strong) {
        GameRouteBody(state = state, levelId = levelId, loader = loader, onGameFinished = onGameFinished, onBack = onBack)
    }
}

@Composable
private fun GameRouteBody(
    state: GameLoadState,
    levelId: String,
    loader: GameLoadViewModel,
    onGameFinished: (GameOutcome) -> Unit,
    onBack: () -> Unit,
) {
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
        ScreenTopBar(title = SemesterLevel.fromId(levelId)?.name.orEmpty(), onBack = onBack)
        content()
    }
}
