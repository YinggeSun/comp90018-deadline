package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.GameState

/** Pure-Kotlin session API; tray insertion, matching, and win/loss evaluation remain deferred. */
interface GameEngine {
    /** Current read-only snapshot; only the engine can replace its state. */
    val state: GameState

    /** Removes a selectable tile; covered, unknown, or removed IDs leave state unchanged. */
    fun selectTile(tileId: String)

    /** True only for a tile still on the board with no active covering tiles. */
    fun isTileSelectable(tileId: String): Boolean

    /** Placeholder for a later undo implementation; currently leaves state unchanged. */
    fun undo()

    /** Placeholder for a later shuffle implementation; currently leaves state unchanged. */
    fun shuffle()

    /** Restores the initial board and availability, empty tray, and running status. */
    fun restart()
}
