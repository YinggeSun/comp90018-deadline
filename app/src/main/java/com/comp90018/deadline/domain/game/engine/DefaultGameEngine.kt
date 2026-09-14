package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.level.model.Level

/**
 * Base engine initialized from [Level.board], with the default empty tray and running status.
 * Initialization and restart are implemented; selection, undo, and shuffle are deferred.
 * The supplied board follows the models' contract that its tile list is not mutated externally.
 */
class DefaultGameEngine(level: Level) : GameEngine {
    private val initialState = GameState(board = level.board)
    private var currentState = initialState

    /** Current snapshot, exposed without a public setter. */
    override val state: GameState
        get() = currentState

    /** Intentional no-op until selection gameplay is implemented in a later issue. */
    override fun selectTile(tileId: String) = Unit

    /** Intentional no-op; no undo history is maintained in the base engine. */
    override fun undo() = Unit

    /** Intentional no-op; the base engine does not randomize tiles. */
    override fun shuffle() = Unit

    /** Replaces runtime state with the original level-derived snapshot. */
    override fun restart() {
        currentState = initialState
    }
}
