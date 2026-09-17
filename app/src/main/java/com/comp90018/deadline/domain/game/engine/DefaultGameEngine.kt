package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.level.model.Level

/**
 * Base engine initialized from [Level.board], with the default empty tray and running status.
 * Selection removes tiles only; tray insertion, matching, win/loss, undo, and shuffle are deferred.
 * The supplied board follows the models' contract that its tile list is not mutated externally.
 */
class DefaultGameEngine(level: Level) : GameEngine {
    private val initialState = GameState(board = level.board)
    private val overlapGraph = OverlapGraph(level.board)
    private var currentState = initialState

    /** Current snapshot, exposed without a public setter. */
    override val state: GameState
        get() = currentState

    /** Updates only covered neighbours, then copies the board while preserving tray and status. */
    override fun selectTile(tileId: String) {
        if (!overlapGraph.remove(tileId)) return
        currentState = currentState.copy(
            board = Board(currentState.board.tiles.filterNot { it.id == tileId })
        )
    }

    override fun isTileSelectable(tileId: String): Boolean = overlapGraph.isSelectable(tileId)

    /** Intentional no-op; no undo history is maintained in the base engine. */
    override fun undo() = Unit

    /** Intentional no-op; the base engine does not randomize tiles. */
    override fun shuffle() = Unit

    /** Replaces runtime state with the original level-derived snapshot. */
    override fun restart() {
        overlapGraph.reset()
        currentState = initialState
    }
}
