package com.comp90018.deadline.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
<<<<<<< HEAD
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.game.engine.CompletionTimer
=======
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.stress.StressManager
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
<<<<<<< HEAD
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
=======
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4

/**
 * Owns one game session. Forwards taps to the [GameEngine] and republishes
 * its state as [GameUiState]; all game rules stay in the engine.
<<<<<<< HEAD
 *
 * Also owns the session's [CompletionTimer]: it starts with the level, stops
 * once the game is won or lost, and restarts with the level. Each win is passed to
 * [completionRecorder] with the timer's final reading, stamped by [nowMillis].
 * [personalBestMillis] returns the level's currently saved best, if any.
=======
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
 */
class GameViewModel(
    levelId: String,
    findLevel: (String) -> Level? = ::findFixedLevel,
<<<<<<< HEAD
    private val timer: CompletionTimer = CompletionTimer(),
    private val completionRecorder: CompletionRecorder = CompletionRecorder.None,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val personalBestMillis: (levelId: String) -> Long? = { null },
    createEngine: (Level) -> GameEngine = { DefaultGameEngine(it, week = it.week) },
) : ViewModel(), GameSensorActions {
    private val level = findLevel(levelId)
    private val engine = level?.let(createEngine)

    init {
        if (engine != null) timer.start()
    }

    private val _uiState =
        MutableStateFlow(
            if (level == null || engine == null) {
                GameUiState(levelNotFound = true)
            } else {
                initialUiState(level, engine)
            },
        )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    /**
     * Whole seconds on the completion timer, emitted when the value changes.
     * Cold flow: it ticks only while the screen collects it.
     */
    val elapsedSeconds: Flow<Long> =
        flow {
            while (true) {
                emit(timer.elapsedSeconds)
                delay(TIMER_TICK_MILLIS)
            }
        }.distinctUntilChanged()

    /** Elapsed time on the completion timer; frozen once the game has ended. */
    val elapsedMillis: Long
        get() = timer.elapsedMillis

    fun onEvent(event: GameUiEvent) = onEvent(event, null)

    fun onEvent(
        event: GameUiEvent,
        haptics: HapticFeedbackManager?,
    ) {
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
        if (event == GameUiEvent.RestartClicked) _uiState.value = _uiState.value.copy(peekAmount = 0f, previousBestMillis = null)
        val after = engine.state
        if (after.board.tiles.size == before.board.tiles.size - 1) {
            haptics?.perform(
                when {
                    after.status == GameStatus.WON -> GameHaptic.SUCCESS
                    after.status == GameStatus.LOST -> GameHaptic.FAILURE
                    after.taskTray.tiles.size < before.taskTray.tiles.size + 1 -> GameHaptic.MATCH
                    else -> GameHaptic.TILE_SELECT
                },
            )
        }
    }

=======
    createEngine: (Level) -> GameEngine = { DefaultGameEngine(it) }
) : ViewModel(), GameSensorActions {

    private val level = findLevel(levelId)
    private val engine = level?.let(createEngine)

    private val _uiState = MutableStateFlow(
        if (level == null || engine == null) {
            GameUiState(levelNotFound = true)
        } else {
            initialUiState(level, engine)
        }
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun onEvent(event: GameUiEvent) = onEvent(event, null)

    fun onEvent(event: GameUiEvent, haptics: HapticFeedbackManager?) {
        val engine = engine ?: return
        val before = engine.state
        when (event) {
            is GameUiEvent.TileTapped -> engine.selectTile(event.tileId)
        }
        _uiState.value = _uiState.value.withEngineState(engine)
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

>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
    override fun onShuffleRequested(): Boolean {
        val engine = engine ?: return false
        val before = engine.state
        engine.shuffle()
<<<<<<< HEAD
        publish(engine)
=======
        _uiState.value = _uiState.value.withEngineState(engine)
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
        return engine.state != before
    }

    override fun onPeekChanged(amount: Float) {
<<<<<<< HEAD
        _uiState.value =
            _uiState.value.copy(
                peekAmount = if (amount.isFinite()) amount.coerceIn(0f, 1f) else 0f,
            )
    }

    fun undo() = onEvent(GameUiEvent.UndoClicked)

    fun restart() = onEvent(GameUiEvent.RestartClicked)

    private fun initialUiState(
        level: Level,
        engine: GameEngine,
    ): GameUiState {
        val tiles = engine.state.board.tiles
        return GameUiState(
            levelName = level.name,
            boardRows = tiles.maxOfOrNull { it.position.row + TILE_SPAN } ?: 0,
            boardColumns = tiles.maxOfOrNull { it.position.column + TILE_SPAN } ?: 0,
        ).withEngineState(engine)
    }

    /**
     * Republishes the engine state and stops the timer once the game has ended. On a win, the
     * previous Personal Best is read before the new time is recorded and published together
     * with the WON status, so no observer sees the win without it.
     */
    private fun publish(engine: GameEngine) {
        val justWon = _uiState.value.status != GameStatus.WON && engine.state.status == GameStatus.WON
        val previousBest = if (justWon) level?.let { personalBestMillis(it.id) } else _uiState.value.previousBestMillis
        _uiState.value = _uiState.value.withEngineState(engine).copy(previousBestMillis = previousBest)
        if (engine.state.status != GameStatus.RUNNING) timer.stop()
        if (justWon) recordWin()
    }

    private fun recordWin() {
        val level = level ?: return
        completionRecorder.record(
            CompletionResult(
                levelId = level.id,
                week = level.week,
                // A sub-millisecond win still counts as a completion.
                timeMillis = timer.elapsedMillis.coerceAtLeast(1L),
                completedAtMillis = nowMillis(),
            ),
        )
    }

    private fun GameUiState.withEngineState(engine: GameEngine): GameUiState {
        val state: GameState = engine.state
        val stressManager = StressManager(engine.stressConfig)
        return copy(
            boardTiles =
                state.board.tiles
                    .sortedWith(compareBy({ it.position.layer }, { it.position.row }, { it.position.column }))
                    .map { it.toUiModel(isSelectable = engine.isTileSelectable(it.id)) },
            trayTiles = state.taskTray.tiles.map { it.toUiModel(isSelectable = false) },
            trayCapacity = state.taskTray.capacity,
            status = state.status,
            stress = stressManager.clamp(state.stress),
            stressMaximum = engine.stressConfig.maximum,
            isHighStress = stressManager.isHighStress(state.stress),
            canUndo = engine.canUndo,
        )
    }

    private fun Tile.toUiModel(isSelectable: Boolean) =
        TileUiModel(
            id = id,
            type = type,
            row = position.row,
            column = position.column,
            layer = position.layer,
            isSelectable = isSelectable,
        )

    companion object {
        /** A tile covers this many logical units in each direction (see TilePosition). */
        const val TILE_SPAN = 2

        private const val TIMER_TICK_MILLIS = 200L

        fun factory(levelId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    GameViewModel(
                        levelId,
                        completionRecorder = container.completionRecorder,
                        personalBestMillis = { container.currentProgress.value.bestFor(it)?.timeMillis },
                    )
                }
            }
    }
}

private fun findFixedLevel(levelId: String): Level? = (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL).find { it.id == levelId }
=======
        _uiState.value = _uiState.value.copy(
            peekAmount = if (amount.isFinite()) amount.coerceIn(0f, 1f) else 0f
        )
    }

    fun undo() {
        val engine = engine ?: return
        engine.undo()
        _uiState.value = _uiState.value.withEngineState(engine)
    }

    fun restart() {
        val engine = engine ?: return
        engine.restart()
        _uiState.value = _uiState.value.withEngineState(engine).copy(peekAmount = 0f)
    }

    private fun initialUiState(level: Level, engine: GameEngine): GameUiState {
        val tiles = engine.state.board.tiles
        return GameUiState(
            levelName = level.name,
            boardRows = tiles.maxOfOrNull { it.position.row + TILE_SPAN } ?: 0,
            boardColumns = tiles.maxOfOrNull { it.position.column + TILE_SPAN } ?: 0
        ).withEngineState(engine)
    }

    private fun GameUiState.withEngineState(engine: GameEngine): GameUiState {
        val state: GameState = engine.state
        return copy(
            boardTiles = state.board.tiles
                .sortedWith(compareBy({ it.position.layer }, { it.position.row }, { it.position.column }))
                .map { it.toUiModel(isSelectable = engine.isTileSelectable(it.id)) },
            trayTiles = state.taskTray.tiles.map { it.toUiModel(isSelectable = false) },
            trayCapacity = state.taskTray.capacity,
            status = state.status
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

        fun factory(levelId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(levelId) }
        }
    }
}

private fun findFixedLevel(levelId: String): Level? =
    (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL).find { it.id == levelId }
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
