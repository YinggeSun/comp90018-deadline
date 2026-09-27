package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.GameState

/** Pure-Kotlin session API with deterministic win/loss evaluation after matching. */
interface GameEngine {
    /** Current read-only snapshot; only the engine can replace its state. */
    val state: GameState

    /** Resolves selection, matching, then win/loss; terminal games, unavailable IDs and full trays are no-ops. */
    fun selectTile(tileId: String)

    /** True only while running, for a tile still on the board with no active covering tiles. */
    fun isTileSelectable(tileId: String): Boolean

    /** Placeholder for a later undo implementation; currently leaves state unchanged. */
    fun undo()

    /** Placeholder for a later shuffle implementation; currently leaves state unchanged. */
    fun shuffle()

    /** Restores the initial board and availability, empty tray, and running status. */
    fun restart()
}
