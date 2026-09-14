package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.GameState

/** Pure-Kotlin session API. Issue #6 implements initialization and restart only. */
interface GameEngine {
    /** Current read-only snapshot; only the engine can replace its state. */
    val state: GameState

    /** Placeholder for later selection gameplay; currently leaves state unchanged. */
    fun selectTile(tileId: String)

    /** Placeholder for a later undo implementation; currently leaves state unchanged. */
    fun undo()

    /** Placeholder for a later shuffle implementation; currently leaves state unchanged. */
    fun shuffle()

    /** Restores the level's initial board, empty tray, and running status. */
    fun restart()
}
