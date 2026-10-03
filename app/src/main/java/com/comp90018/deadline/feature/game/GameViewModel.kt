package com.comp90018.deadline.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.domain.game.engine.CompletionTimer
import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.stress.StressManager
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Owns one game session. Forwards taps to the [GameEngine] and republishes
 * its state as [GameUiState]; all game rules stay in the engine.
 *
 * Also owns the session's [CompletionTimer]: it starts with the level, stops
 * once the game is won or lost, and restarts with the level.
 */
class GameViewModel(
    levelId: String,
    findLevel: (String) -> Level? = ::findFixedLevel,
    private val timer: CompletionTimer = CompletionTimer(),
    createEngine: (Level) -> GameEngine = { DefaultGameEngine(it) }
) : ViewModel(), GameSensorActions {

    private val level = findLevel(levelId)
    private val engine = level?.let(createEngine)

    init {
        if (engine != null) timer.start()
    }

    private val _uiState = MutableStateFlow(
        if (level == null || engine == null) {
            GameUiState(levelNotFound = true)
        } else {
            initialUiState(level, engine)
        }
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Whole seconds on the completion timer, emitted when the value changes.
     * Cold flow: it ticks only while the screen collects it.
     */
    val elapsedSeconds: Flow<Long> = flow {
        while (true) {
            emit(timer.elapsedSeconds)
            delay(TIMER_TICK_MILLIS)
        }
    }.distinctUntilChanged()

    /** Elapsed time on the completion timer; frozen once the game has ended. */
    val elapsedMillis: Long
        get() = timer.elapsedMillis

    fun onEvent(event: GameUiEvent) = onEvent(event, null)

    fun onEvent(event: GameUiEvent, haptics: HapticFeedbackManager?) {
        val engine = engine ?: return
        val before = engine.state
        when (event) {
            is GameUiEvent.TileTapped -> engine.selectTile(event.tileId)
            GameUiEvent.UndoClicked -> engine.undo()
            GameUiEvent.RestartClicked -> {
                engine.restart()
                timer.restart()
            }
        }
        publish(engine)
        if (event == GameUiEvent.RestartClicked) _uiState.value = _uiState.value.copy(peekAmount = 0f)
        val after = engine.state
        if (after.board.tiles.size == before.board.tiles.size - 1) {
            haptics?.perform(when {
                after.status == GameStatus.WON -> GameHaptic.SUCCESS
                after.status == GameStatus.LOST -> GameHaptic.FAILURE
                after.taskTray.tiles.size < before.taskTray.tiles.size + 1 -> GameHaptic.MATCH
                else -> GameHaptic.TILE_SELECT
            })
        }
    }

    override fun onShuffleRequested(): Boolean {
        val engine = engine ?: return false
        val before = engine.state
        engine.shuffle()
        publish(engine)
        return engine.state != before
    }

    override fun onPeekChanged(amount: Float) {
        _uiState.value = _uiState.value.copy(
            peekAmount = if (amount.isFinite()) amount.coerceIn(0f, 1f) else 0f
        )
    }

    fun undo() = onEvent(GameUiEvent.UndoClicked)

    fun restart() = onEvent(GameUiEvent.RestartClicked)

    private fun initialUiState(level: Level, engine: GameEngine): GameUiState {
        val tiles = engine.state.board.tiles
        return GameUiState(
            levelName = level.name,
            boardRows = tiles.maxOfOrNull { it.position.row + TILE_SPAN } ?: 0,
            boardColumns = tiles.maxOfOrNull { it.position.column + TILE_SPAN } ?: 0
        ).withEngineState(engine)
    }

    /** Republishes the engine state and stops the timer once the game has ended. */
    private fun publish(engine: GameEngine) {
        _uiState.value = _uiState.value.withEngineState(engine)
        if (engine.state.status != GameStatus.RUNNING) timer.stop()
    }

    private fun GameUiState.withEngineState(engine: GameEngine): GameUiState {
        val state: GameState = engine.state
        val stressManager = StressManager(engine.stressConfig)
        return copy(
            boardTiles = state.board.tiles
                .sortedWith(compareBy({ it.position.layer }, { it.position.row }, { it.position.column }))
                .map { it.toUiModel(isSelectable = engine.isTileSelectable(it.id)) },
            trayTiles = state.taskTray.tiles.map { it.toUiModel(isSelectable = false) },
            trayCapacity = state.taskTray.capacity,
            status = state.status,
            stress = stressManager.clamp(state.stress),
            stressMaximum = engine.stressConfig.maximum,
            isHighStress = stressManager.isHighStress(state.stress),
            canUndo = engine.canUndo
        )
    }

    private fun Tile.toUiModel(isSelectable: Boolean) = TileUiModel(
        id = id,
        type = type,
        row = position.row,
        column = position.column,
        layer = position.layer,
        isSelectable = isSelectable
    )

    companion object {
        /** A tile covers this many logical units in each direction (see TilePosition). */
        const val TILE_SPAN = 2

        private const val TIMER_TICK_MILLIS = 200L

        fun factory(levelId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(levelId) }
        }
    }
}

private fun findFixedLevel(levelId: String): Level? =
    (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL).find { it.id == levelId }
