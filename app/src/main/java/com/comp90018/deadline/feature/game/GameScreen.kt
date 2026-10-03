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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.comp90018.deadline.sensor.AndroidSensorGateway
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.ErrorContent
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.feature.game.components.GameActions
import com.comp90018.deadline.feature.game.components.GameBoard
import com.comp90018.deadline.feature.game.components.GameHud
import com.comp90018.deadline.feature.game.components.TaskTray

@Composable
fun GameScreen(
    levelId: String,
    onGameFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(levelId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState(initial = 0L)
    val currentOnGameFinished by rememberUpdatedState(onGameFinished)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = remember(context) { HapticFeedbackManager(context.applicationContext) }
    val binder = remember(context, viewModel) {
        GameSensorBinder(AndroidSensorGateway(context.applicationContext), viewModel, haptics)
    }
    DisposableEffect(owner, binder) {
        owner.lifecycle.addObserver(binder)
        onDispose {
            owner.lifecycle.removeObserver(binder)
            binder.stop()
        }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status != GameStatus.RUNNING) currentOnGameFinished()
    }

    GameContent(
        uiState = uiState,
        elapsedSeconds = elapsedSeconds,
        onEvent = { viewModel.onEvent(it, haptics) },
        onBack = onBack
    )
}

/** Stateless body of the Game screen, driven only by [uiState]. */
@Composable
fun GameContent(
    uiState: GameUiState,
    elapsedSeconds: Long,
    onEvent: (GameUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TertiaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text(
                text = uiState.levelName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (uiState.levelNotFound) {
            ErrorContent(message = stringResource(R.string.game_level_not_found))
        } else {
            GameHud(
                elapsedSeconds = elapsedSeconds,
                stress = uiState.stress,
                stressMaximum = uiState.stressMaximum,
                isHighStress = uiState.isHighStress,
                modifier = Modifier.padding(horizontal = Spacing.large)
            )
            GameBoard(
                tiles = uiState.boardTiles,
                rows = uiState.boardRows,
                columns = uiState.boardColumns,
                onTileClick = { onEvent(GameUiEvent.TileTapped(it)) },
                peekAmount = uiState.peekAmount,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(Spacing.large)
            )
            TaskTray(
                tiles = uiState.trayTiles,
                capacity = uiState.trayCapacity,
                modifier = Modifier.padding(horizontal = Spacing.large)
            )
            GameActions(
                canUndo = uiState.canUndo,
                onUndo = { onEvent(GameUiEvent.UndoClicked) },
                onRestart = { onEvent(GameUiEvent.RestartClicked) },
                modifier = Modifier.padding(Spacing.medium)
            )
        }
    }
}
