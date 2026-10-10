package com.comp90018.deadline.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.core.audio.GameAudio
import com.comp90018.deadline.core.audio.GameAudioEvent
import com.comp90018.deadline.domain.game.engine.CompletionTimer
import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.stress.StressManager
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.settings.PlayerSettings
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive

/**
 * Owns one game session. Forwards taps to the [GameEngine] and republishes
 * its state as [GameUiState]; all game rules stay in the engine.
 *
 * Also owns the session's [CompletionTimer]: it starts with the level, stops
 * once the game is won or lost, and restarts with the level. Each win is passed to
 * [completionRecorder] with the timer's final reading, stamped by [nowMillis].
 * [personalBestMillis] returns the level's currently saved best, if any.
 *
 * Stress grows with active play time: the screen runs [runStressClock] while it is resumed,
 * which feeds the engine the time measured by [stressClockNanos]. Time spent with the screen
 * paused therefore adds no stress.
 */
class GameViewModel(
    levelId: String,
    findLevel: (String) -> Level? = ::findFixedLevel,
    private val timer: CompletionTimer = CompletionTimer(),
    private val completionRecorder: CompletionRecorder = CompletionRecorder.None,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val personalBestMillis: (levelId: String) -> Long? = { null },
    settings: Flow<PlayerSettings> = flowOf(PlayerSettings()),
    private val audio: GameAudio = GameAudio.None,
    private val stressClockNanos: () -> Long = System::nanoTime,
    createEngine: (Level) -> GameEngine = { DefaultGameEngine(it, week = it.week) },
) : ViewModel(), GameSensorActions {
    private val audioSession = Any()
    private var audioForeground = false

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

    /**
     * The player's stored settings, for the screen to switch haptics and motion controls
     * on or off. Cold like [elapsedSeconds]; it is read while the Game screen collects it.
     */
    val settings: Flow<PlayerSettings> = settings

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
        // The engine removes exactly one board tile for an accepted selection, including redirects.
        // Tray delta +1 -3 identifies a resolved triple of any type (including Coffee and Music).
        if (event is GameUiEvent.TileTapped && after.board.tiles.size == before.board.tiles.size - 1) {
            audio.play(audioSession, GameAudioEvent.TILE_CLICK)
            if (after.taskTray.tiles.size == before.taskTray.tiles.size - 2) {
                audio.play(audioSession, GameAudioEvent.MATCH)
            }
        }
        playMaximumStressWarning(before, after, engine.stressConfig.maximum)
        setAudioForeground(audioForeground)
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

    /** Screen lifecycle controls visibility; the ViewModel retains the session across rotation. */
    fun setAudioForeground(foreground: Boolean) {
        audioForeground = foreground
        audio.setForeground(audioSession, foreground && engine != null, engine?.state?.status == GameStatus.RUNNING)
    }

    override fun onCleared() {
        audio.release(audioSession)
    }

    override fun onShuffleRequested(): Boolean {
        val engine = engine ?: return false
        val before = engine.state
        engine.shuffle()
        publish(engine)
        return engine.state != before
    }

    override fun onPeekChanged(amount: Float) {
        _uiState.value =
            _uiState.value.copy(
                peekAmount = if (amount.isFinite()) amount.coerceIn(0f, 1f) else 0f,
            )
    }

    /**
     * Feeds [elapsedMillis] of active play to the engine's Stress System and republishes.
     * Entering Maximum Stress fires audio and [GameHaptic.MAX_STRESS] warnings once.
     */
    fun onTimeElapsed(
        elapsedMillis: Long,
        haptics: HapticFeedbackManager? = null,
    ) {
        val engine = engine ?: return
        if (engine.state.status != GameStatus.RUNNING) return
        val before = engine.state
        val wasMaxStress = _uiState.value.isMaxStress
        engine.advanceTime(elapsedMillis)
        publish(engine)
        playMaximumStressWarning(before, engine.state, engine.stressConfig.maximum)
        setAudioForeground(audioForeground)
        if (!wasMaxStress && _uiState.value.isMaxStress) haptics?.perform(GameHaptic.MAX_STRESS)
    }

    private fun playMaximumStressWarning(
        before: GameState,
        after: GameState,
        maximum: Int,
    ) {
        if (
            before.status == GameStatus.RUNNING && after.status == GameStatus.RUNNING &&
            before.stress < maximum && after.stress >= maximum
        ) {
            audio.play(audioSession, GameAudioEvent.STRESS_MAX)
        }
    }

    /**
     * Ticks the Stress System until cancelled. Each run measures time from its own start, so
     * cancelling it while the screen is paused and starting it again on resume skips the pause.
     */
    suspend fun runStressClock(haptics: HapticFeedbackManager? = null) {
        var last = stressClockNanos()
        while (currentCoroutineContext().isActive) {
            delay(STRESS_TICK_MILLIS)
            val now = stressClockNanos()
            val elapsedMillis = ((now - last) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)
            // Keep the sub-millisecond remainder for the next tick.
            last += elapsedMillis * NANOS_PER_MILLISECOND
            onTimeElapsed(elapsedMillis, haptics)
        }
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
                // Record the level's last week: clearing week N unlocks week N + 1, which is
                // the first week of the next level.
                week = SemesterDifficulty.lastWeekForLevel(level.levelNumber),
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
            isMaxStress = state.status == GameStatus.RUNNING && stressManager.isMaxStress(state.stress),
            isStressSlowed = state.status == GameStatus.RUNNING && state.musicSlowdownRemainingMillis > 0L,
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

        /** How often the stress gauge advances; short enough that the bar moves smoothly. */
        private const val STRESS_TICK_MILLIS = 100L

        private const val NANOS_PER_MILLISECOND = 1_000_000L

        /** A ViewModel for [level], whose board is already generated. */
        fun factory(level: Level): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    GameViewModel(
                        level.id,
                        findLevel = { level },
                        completionRecorder = container.completionRecorder,
                        personalBestMillis = { container.currentProgress.value.bestFor(it)?.timeMillis },
                        settings = container.settingsRepository.settings,
                        audio = container.gameAudioManager,
                    )
                }
            }
    }
}

private fun findFixedLevel(levelId: String): Level? = (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL).find { it.id == levelId }
