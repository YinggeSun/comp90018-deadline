package com.comp90018.deadline.feature.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
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
import com.comp90018.deadline.feature.game.components.minimumBoardSize
import com.comp90018.deadline.sensor.AndroidSensorGateway
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager

@Composable
fun GameScreen(
    levelId: String,
    onGameFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: GameViewModel = viewModel(factory = GameViewModel.factory(levelId)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState(initial = 0L)
    val currentOnGameFinished by rememberUpdatedState(onGameFinished)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = remember(context) { HapticFeedbackManager(context.applicationContext) }
    val binder =
        remember(context, viewModel) {
            GameSensorBinder(AndroidSensorGateway(context.applicationContext), viewModel, haptics)
        }
    // Null until stored settings are read, so sensors never start with the defaults first.
    val settings by viewModel.settings.collectAsState(initial = null)
    val settingsLoaded = settings != null
    SideEffect {
        settings?.let {
            haptics.enabled = it.hapticsEnabled
            binder.shakeEnabled = it.shakeToShuffleEnabled
            binder.tiltEnabled = it.tiltToPeekEnabled
        }
    }
    DisposableEffect(owner, binder, settingsLoaded) {
        if (settingsLoaded) owner.lifecycle.addObserver(binder)
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
        onBack = onBack,
    )
}

/**
 * Stateless body of the Game screen, driven only by [uiState].
 *
 * The board takes whatever height the header and footer leave, but never
 * less than [minimumBoardSize]. When the window is too short for that (for
 * example landscape with large fonts), the whole screen scrolls instead.
 */
@Composable
fun GameContent(
    uiState: GameUiState,
    elapsedSeconds: Long,
    onEvent: (GameUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBar = @Composable {
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
                text = uiState.levelName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    if (uiState.levelNotFound) {
        Column(modifier = modifier.fillMaxSize()) {
            topBar()
            ErrorContent(message = stringResource(R.string.game_level_not_found))
        }
        return
    }

    val minimumBoard = minimumBoardSize(uiState.boardRows, uiState.boardColumns)
    GameLayout(
        minimumBoardHeight = minimumBoard.height + BoardPadding * 2,
        modifier = modifier,
        header = {
            Column {
                topBar()
                GameHud(
                    elapsedSeconds = elapsedSeconds,
                    stress = uiState.stress,
                    stressMaximum = uiState.stressMaximum,
                    isHighStress = uiState.isHighStress,
                    modifier = Modifier.padding(horizontal = Spacing.large),
                )
            }
        },
        board = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Very wide boards scroll sideways rather than drop below the minimum tile size.
                val boardWidth = max(maxWidth, minimumBoard.width + BoardPadding * 2)
                Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    GameBoard(
                        tiles = uiState.boardTiles,
                        rows = uiState.boardRows,
                        columns = uiState.boardColumns,
                        onTileClick = { onEvent(GameUiEvent.TileTapped(it)) },
                        peekAmount = uiState.peekAmount,
                        modifier =
                            Modifier
                                .width(boardWidth)
                                .fillMaxHeight()
                                .padding(BoardPadding),
                    )
                }
            }
        },
        footer = {
            Column {
                TaskTray(
                    tiles = uiState.trayTiles,
                    capacity = uiState.trayCapacity,
                    modifier = Modifier.padding(horizontal = Spacing.large),
                )
                GameActions(
                    canUndo = uiState.canUndo,
                    onUndo = { onEvent(GameUiEvent.UndoClicked) },
                    onRestart = { onEvent(GameUiEvent.RestartClicked) },
                    modifier = Modifier.padding(Spacing.medium),
                )
            }
        },
    )
}

private val BoardPadding = Spacing.large

/**
 * Stacks [header], [board] and [footer]. The board fills the height left in
 * the viewport, but at least [minimumBoardHeight]; if the total no longer
 * fits, the column scrolls vertically.
 */
@Composable
private fun GameLayout(
    minimumBoardHeight: Dp,
    header: @Composable () -> Unit,
    board: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = constraints.maxHeight
        Layout(
            contents = listOf(header, board, footer),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) { (headerMeasurables, boardMeasurables, footerMeasurables), constraints ->
            val width = constraints.maxWidth
            val loose = Constraints(maxWidth = width)
            val headers = headerMeasurables.map { it.measure(loose) }
            val footers = footerMeasurables.map { it.measure(loose) }
            val used = headers.sumOf { it.height } + footers.sumOf { it.height }
            val boardHeight = maxOf(viewportHeight - used, minimumBoardHeight.roundToPx())
            val boards = boardMeasurables.map { it.measure(Constraints.fixed(width, boardHeight)) }

            layout(width, used + boardHeight) {
                var y = 0
                (headers + boards + footers).forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height
                }
            }
        }
    }
}
