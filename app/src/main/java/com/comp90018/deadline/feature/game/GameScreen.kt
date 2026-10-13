package com.comp90018.deadline.feature.game

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.ErrorContent
import com.comp90018.deadline.core.ui.components.ScreenTopBar
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.feature.game.components.GameActions
import com.comp90018.deadline.feature.game.components.GameBoard
import com.comp90018.deadline.feature.game.components.GameHud
import com.comp90018.deadline.feature.game.components.TaskTray
import com.comp90018.deadline.feature.game.components.minimumBoardSize
import com.comp90018.deadline.feature.settings.SettingsViewModel
import com.comp90018.deadline.sensor.AndroidSensorGateway
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
import com.comp90018.deadline.sensor.light.AutoLightController

@Composable
fun GameScreen(
    levelId: String,
    onGameFinished: (GameOutcome) -> Unit,
    onBack: () -> Unit,
    viewModel: GameViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState(initial = 0L)
    val currentOnGameFinished by rememberUpdatedState(onGameFinished)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = remember(context) { HapticFeedbackManager(context.applicationContext) }
    val binder =
        remember(context, viewModel) {
            GameSensorBinder(
                AndroidSensorGateway(context.applicationContext),
                viewModel,
                haptics,
                shakeConfig = viewModel.shakeConfig,
                tiltConfig = viewModel.tiltConfig,
            )
        }
    // Null until stored settings are read, so sensors never start with the defaults first.
    val settings by viewModel.settings.collectAsState(initial = null)
    val settingsLoaded = settings != null
    // Save through the same repository as the Settings page.
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    // Attach to the visible game only; restore the former brightness on pause/exit.
    val activity =
        remember(context) {
            var current: android.content.Context = context
            while (current is ContextWrapper && current !is Activity) current = current.baseContext
            current as? Activity
        }
    DisposableEffect(owner, activity, settings?.autoLightEnabled) {
        val controller =
            if (settings?.autoLightEnabled == true && activity != null) {
                AutoLightController(activity)
            } else {
                null
            }
        val observer =
            LifecycleEventObserver { _, _ ->
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    controller?.start()
                } else {
                    controller?.stop()
                }
            }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller?.start()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            controller?.stop()
        }
    }
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

    DisposableEffect(owner, viewModel) {
        val observer =
            LifecycleEventObserver { _, _ ->
                viewModel.setAudioForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        owner.lifecycle.addObserver(observer)
        viewModel.setAudioForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.setAudioForeground(false)
        }
    }

    // Stress only builds while the game is on screen and resumed.
    LaunchedEffect(viewModel, owner, haptics) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.runStressClock(haptics)
        }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status != GameStatus.RUNNING) {
            currentOnGameFinished(
                GameOutcome(
                    won = uiState.status == GameStatus.WON,
                    elapsedMillis = viewModel.elapsedMillis,
                    previousBestMillis = uiState.previousBestMillis,
                ),
            )
        }
    }

    GameContent(
        uiState = uiState,
        elapsedSeconds = elapsedSeconds,
        onEvent = { viewModel.onEvent(it, haptics) },
        onBack = onBack,
        autoLightEnabled = settings?.autoLightEnabled,
        onAutoLightChange = settingsViewModel::setAutoLightEnabled,
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
    autoLightEnabled: Boolean? = null,
    onAutoLightChange: (Boolean) -> Unit = {},
) {
    val topBar = @Composable {
        ScreenTopBar(title = uiState.levelName, onBack = onBack) {
            // Available on the game screen; persists via SettingsRepository.
            TextButton(
                onClick = { autoLightEnabled?.let { onAutoLightChange(!it) } },
                enabled = autoLightEnabled != null,
                // Width-limited, so with large fonts this label wraps and the level name stays whole.
                modifier = Modifier.widthIn(max = AutoLightToggleMaxWidth).testTag(AUTO_LIGHT_GAME_TOGGLE_TAG),
            ) {
                Text(
                    text =
                        stringResource(
                            if (autoLightEnabled == true) R.string.game_auto_light_on else R.string.game_auto_light_off,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                )
            }
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
        modifier = modifier.maxStressFlash(uiState.isMaxStress),
        header = {
            Column {
                topBar()
                GameHud(
                    elapsedSeconds = elapsedSeconds,
                    stress = uiState.stress,
                    stressMaximum = uiState.stressMaximum,
                    isHighStress = uiState.isHighStress,
                    isMaxStress = uiState.isMaxStress,
                    isStressSlowed = uiState.isStressSlowed,
                    modifier = Modifier.padding(horizontal = Spacing.large),
                )
                if (uiState.isMaxStress) {
                    Text(
                        text = stringResource(R.string.game_stress_max_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier =
                            Modifier
                                .testTag(MAX_STRESS_WARNING_TAG)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.large, vertical = Spacing.extraSmall)
                                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                                .padding(Spacing.small),
                    )
                }
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
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(Spacing.medium),
                )
            }
        },
    )
}

private val BoardPadding = Spacing.large
private val AutoLightToggleMaxWidth = 112.dp

const val MAX_STRESS_WARNING_TAG = "max_stress_warning"
const val AUTO_LIGHT_GAME_TOGGLE_TAG = "auto_light_game_toggle"

/**
 * Pulsing red frame drawn over the whole screen while [active], for the Maximum Stress
 * warning. It only draws, so taps still reach the board underneath.
 */
@Composable
private fun Modifier.maxStressFlash(active: Boolean): Modifier {
    if (!active) return this
    val alpha by rememberInfiniteTransition(label = "maxStress").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500, easing = LinearEasing), RepeatMode.Reverse),
        label = "maxStressAlpha",
    )
    return border(6.dp, MaterialTheme.colorScheme.error.copy(alpha = alpha))
}

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
