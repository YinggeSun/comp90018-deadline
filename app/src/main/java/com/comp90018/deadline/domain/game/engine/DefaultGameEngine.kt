package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.game.model.TrayState
import com.comp90018.deadline.domain.game.shuffle.BoardShuffler
import com.comp90018.deadline.domain.game.stress.CoffeeRecovery
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.domain.game.stress.StressManager
import com.comp90018.deadline.domain.level.model.Level

/**
 * Base engine initialized from [Level.board], with the default empty tray and running status.
 * Selection moves tiles into the tray and immediately removes triples of the selected type.
 * Final board/tray state determines win/loss.
 * [maxUndoDepth] defaults to multi-step history (up to [Int.MAX_VALUE] moves).
 * Zero disables undo; positive values limit retained moves, discarding the oldest first.
 * Completed matches permanently commit their tiles and clear all undo history.
 * The supplied board follows the models' contract that its tile list is not mutated externally.
 *
 * Stress lives in [GameState.stress] and changes only through selections. One accumulation
 * step happens per accepted selection at the [week] rate, and a completed Coffee triple then
 * applies Coffee Recovery. Rejected selections leave stress untouched, undo restores the
 * earlier value with the rest of the snapshot, and [restart] returns it to [initialStress].
 * [week] comes from the caller because [Level] does not yet carry its semester week;
 * [stressConfig] carries the tuning so no gameplay number is hard-coded here.
 */
class DefaultGameEngine(
    level: Level,
    private val maxUndoDepth: Int = Int.MAX_VALUE,
    private val boardShuffler: BoardShuffler = BoardShuffler(),
    private val week: Int = DEFAULT_WEEK,
    override val stressConfig: StressConfig = StressConfig(),
    initialStress: Int = 0,
) : GameEngine {
    init {
        require(maxUndoDepth >= 0) { "Maximum undo depth must be non-negative." }
        require(week > 0) { "Week must be positive." }
    }

    private data class UndoEntry(val state: GameState, val graph: OverlapGraph.Snapshot)

    private val stressManager = StressManager(stressConfig)
    private val coffeeRecovery = CoffeeRecovery(stressConfig)
    private val history = ArrayDeque<UndoEntry>()
    private val initialState =
        GameState(board = level.board, stress = stressManager.clamp(initialStress))
    private val overlapGraph = OverlapGraph(level.board)
    private var currentState = initialState

    /** Current snapshot, exposed without a public setter. */
    override val state: GameState
        get() = currentState

    /** Moves the exact board tile into the tray after validating availability and capacity. */
    override fun selectTile(tileId: String) {
        if (currentState.status != GameStatus.RUNNING) return
        val tile = currentState.board.tiles.find { it.id == tileId } ?: return
        if (!overlapGraph.isSelectable(tileId)) return
        if (currentState.taskTray.isFull) return
        val entry = if (maxUndoDepth > 0) UndoEntry(currentState, overlapGraph.snapshot()) else null
        if (!overlapGraph.remove(tileId)) return
        val board = Board(currentState.board.tiles.filterNot { it.id == tileId })
        val appendedTray = currentState.taskTray.copy(tiles = currentState.taskTray.tiles + tile)
        val matched = appendedTray.tiles.count { it.type == tile.type } == 3
        val tray =
            if (matched) {
                appendedTray.copy(tiles = appendedTray.tiles.filterNot { it.type == tile.type })
            } else {
                appendedTray
            }
        currentState =
            currentState.copy(
                board = board,
                taskTray = tray,
                status = determineStatus(board, tray),
                stress = stressAfterSelection(matchedType = if (matched) tile.type else null),
            )
        if (matched) {
            history.clear()
        } else if (entry != null) {
            if (history.size == maxUndoDepth) history.removeFirst()
            history.addLast(entry)
        }
    }

    /**
     * Stress after one accepted selection: the week's accumulation step, then Coffee Recovery
     * when that selection completed a Coffee triple. A null [matchedType] means no triple was
     * completed, so only accumulation applies.
     */
    private fun stressAfterSelection(matchedType: TileType?): Int =
        coffeeRecovery.applyMatch(
            current = stressManager.accumulate(currentState.stress, week),
            matchedType = matchedType,
            week = week,
        )

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

    /** Restores one pre-selection snapshot; terminal games and empty history are no-ops. */
    override fun undo() {
        if (currentState.status != GameStatus.RUNNING) return
        val entry = history.removeLastOrNull() ?: return
        overlapGraph.restore(entry.graph)
        currentState = entry.state
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
        history.clear()
    }

    companion object {
        /** First semester week, used until a caller supplies the level's own week. */
        const val DEFAULT_WEEK = 1
    }
}
