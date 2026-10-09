package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TrayState
import com.comp90018.deadline.domain.game.shuffle.BoardShuffler
import com.comp90018.deadline.domain.game.stress.CoffeeRecovery
import com.comp90018.deadline.domain.game.stress.InputDegradation
import com.comp90018.deadline.domain.game.stress.MusicRecovery
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.domain.game.stress.StressManager
import com.comp90018.deadline.domain.level.model.Level
import kotlin.math.floor
import kotlin.random.Random

/**
 * Base engine initialized from [Level.board], with the default empty tray and running status.
 * Selection moves tiles into the tray and immediately removes triples of the selected type.
 * Final board/tray state determines win/loss.
 * [maxUndoDepth] defaults to multi-step history (up to [Int.MAX_VALUE] moves).
 * Zero disables undo; positive values limit retained moves, discarding the oldest first.
 * Completed matches permanently commit their tiles and clear all undo history.
 * The supplied board follows the models' contract that its tile list is not mutated externally.
 *
 * Stress lives in [GameState.stress]. It grows only through [advanceTime], and a completed
 * Coffee triple applies Coffee Recovery at the [week] rate; selecting a tile never adds stress.
 * A completed Music triple removes a little stress and starts the Music slowdown tracked in
 * [GameState.musicSlowdownRemainingMillis]. Undo restores the board and tray but keeps the
 * current stress and slowdown, so it cannot rewind time, and [restart] resets both. At Maximum Stress a selection may slip onto a
 * neighbouring selectable tile, decided by [random]. [stressConfig] carries the tuning so no
 * gameplay number is hard-coded here.
 */
class DefaultGameEngine(
    level: Level,
    private val maxUndoDepth: Int = Int.MAX_VALUE,
    private val boardShuffler: BoardShuffler = BoardShuffler(),
    private val week: Int = DEFAULT_WEEK,
    override val stressConfig: StressConfig = StressConfig(),
    initialStress: Int = 0,
    random: Random = Random.Default,
) : GameEngine {
    init {
        require(maxUndoDepth >= 0) { "Maximum undo depth must be non-negative." }
        require(week > 0) { "Week must be positive." }
    }

    private data class UndoEntry(val state: GameState, val graph: OverlapGraph.Snapshot)

    private val stressManager = StressManager(stressConfig)
    private val coffeeRecovery = CoffeeRecovery(stressConfig)
    private val musicRecovery = MusicRecovery(stressConfig)
    private val inputDegradation = InputDegradation(stressConfig, random)

    /** Fraction of a stress point accumulated by [advanceTime] but not yet in the state. */
    private var pendingStress = 0.0
    private val history = ArrayDeque<UndoEntry>()
    private val initialState =
        GameState(board = level.board, stress = stressManager.clamp(initialStress))
    private val overlapGraph = OverlapGraph(level.board)
    private var currentState = initialState

    /** Current snapshot, exposed without a public setter. */
    override val state: GameState
        get() = currentState

    /**
     * Moves a board tile into the tray after validating availability and capacity. The
     * requested tile is used unless Maximum Stress redirects the selection; see [resolveTile].
     */
    override fun selectTile(tileId: String) {
        if (currentState.status != GameStatus.RUNNING) return
        val requested = currentState.board.tiles.find { it.id == tileId } ?: return
        if (!overlapGraph.isSelectable(tileId)) return
        if (currentState.taskTray.isFull) return
        val tile = resolveTile(requested)
        val entry = if (maxUndoDepth > 0) UndoEntry(currentState, overlapGraph.snapshot()) else null
        if (!overlapGraph.remove(tile.id)) return
        val board = Board(currentState.board.tiles.filterNot { it.id == tile.id })
        val appendedTray = currentState.taskTray.copy(tiles = currentState.taskTray.tiles + tile)
        val matched = appendedTray.tiles.count { it.type == tile.type } == 3
        val tray =
            if (matched) {
                appendedTray.copy(tiles = appendedTray.tiles.filterNot { it.type == tile.type })
            } else {
                appendedTray
            }
        val matchedType = if (matched) tile.type else null
        val afterCoffee = coffeeRecovery.applyMatch(currentState.stress, matchedType, week)
        currentState =
            currentState.copy(
                board = board,
                taskTray = tray,
                status = determineStatus(board, tray),
                stress = musicRecovery.applyMatch(afterCoffee, matchedType),
                musicSlowdownRemainingMillis =
                    musicRecovery.slowdownAfterMatch(currentState.musicSlowdownRemainingMillis, matchedType),
            )
        if (matched) {
            history.clear()
        } else if (entry != null) {
            if (history.size == maxUndoDepth) history.removeFirst()
            history.addLast(entry)
        }
    }

    /**
     * The tile a selection of [requested] actually takes. Below Maximum Stress this is always
     * [requested]; at Maximum Stress [inputDegradation] may pick a selectable neighbour instead,
     * and falls back to [requested] when there is none.
     */
    private fun resolveTile(requested: Tile): Tile {
        if (!stressManager.isMaxStress(currentState.stress)) return requested
        val tiles = currentState.board.tiles
        val selectableIds = tiles.filter { overlapGraph.isSelectable(it.id) }.mapTo(HashSet()) { it.id }
        val resolvedId =
            inputDegradation.resolveSelection(
                requestedTileId = requested.id,
                stress = currentState.stress,
                eligibleNeighbours = inputDegradation.eligibleNeighbours(requested.id, tiles, selectableIds),
            )
        return tiles.find { it.id == resolvedId } ?: requested
    }

    /**
     * Adds the stress for [elapsedMillis] of play, carrying the fractional remainder to the
     * next call, and runs down the Music slowdown. Nothing is banked while stress is already at
     * its maximum, so recovering from Maximum Stress is not immediately undone by time spent
     * there.
     */
    override fun advanceTime(elapsedMillis: Long) {
        require(elapsedMillis >= 0L) { "Elapsed time must be non-negative." }
        if (currentState.status != GameStatus.RUNNING) return
        val slowdown = currentState.musicSlowdownRemainingMillis
        val accumulation = musicRecovery.accumulationFor(elapsedMillis, slowdown)
        val slowdownLeft = musicRecovery.remainingAfter(elapsedMillis, slowdown)
        if (slowdownLeft != slowdown) {
            currentState = currentState.copy(musicSlowdownRemainingMillis = slowdownLeft)
        }
        if (stressManager.isMaxStress(currentState.stress)) {
            pendingStress = 0.0
            return
        }
        pendingStress += accumulation
        // The epsilon stops floating-point drift, e.g. 100 x 0.15 summing to 14.999..., from
        // holding back a point that has been fully earned.
        val whole = floor(pendingStress + ROUNDING_EPSILON)
        pendingStress -= whole
        if (whole <= 0.0) return
        val added = whole.coerceAtMost(stressConfig.maximum.toDouble()).toInt()
        currentState = currentState.copy(stress = stressManager.increaseBy(currentState.stress, added))
    }

    private fun determineStatus(
        board: Board,
        tray: TrayState,
    ): GameStatus =
        when {
            board.tiles.isEmpty() -> GameStatus.WON
            tray.isFull -> GameStatus.LOST
            else -> GameStatus.RUNNING
        }

    override fun isTileSelectable(tileId: String): Boolean = currentState.status == GameStatus.RUNNING && overlapGraph.isSelectable(tileId)

    override val canUndo: Boolean
        get() = currentState.status == GameStatus.RUNNING && history.isNotEmpty()

    /**
     * Restores one pre-selection snapshot except for stress and the Music slowdown, which
     * follow play time rather than moves; terminal games and empty history are no-ops.
     */
    override fun undo() {
        if (currentState.status != GameStatus.RUNNING) return
        val entry = history.removeLastOrNull() ?: return
        overlapGraph.restore(entry.graph)
        val restored = entry.state
        val timeUnchanged =
            restored.stress == currentState.stress &&
                restored.musicSlowdownRemainingMillis == currentState.musicSlowdownRemainingMillis
        // Reuse the snapshot itself when nothing time-based moved, so undo returns the exact instance.
        currentState =
            if (timeUnchanged) {
                restored
            } else {
                restored.copy(
                    stress = currentState.stress,
                    musicSlowdownRemainingMillis = currentState.musicSlowdownRemainingMillis,
                )
            }
    }

    /**
     * Redistributes tile types across the remaining board while preserving tile ids,
     * positions and the current Task Tray. Terminal games and boards with fewer than
     * two remaining tiles ignore shuffle requests.
     *
     * Because id/position pairs do not change, [overlapGraph] remains valid and does
     * not need to be rebuilt after a shuffle.
     */
    override fun shuffle() {
        if (currentState.status != GameStatus.RUNNING) return
        if (currentState.board.tiles.size < 2) return

        val shuffledBoard = boardShuffler.shuffle(currentState.board)
        if (shuffledBoard == currentState.board) return

        currentState = currentState.copy(board = shuffledBoard)
        // Old selection snapshots contain pre-shuffle types and must not resurrect them.
        history.clear()
    }

    /** Replaces runtime state with the original level-derived snapshot. */
    override fun restart() {
        overlapGraph.reset()
        currentState = initialState
        pendingStress = 0.0
        history.clear()
    }

    companion object {
        /** First semester week, used until a caller supplies the level's own week. */
        const val DEFAULT_WEEK = 1

        private const val ROUNDING_EPSILON = 1e-9
    }
}
