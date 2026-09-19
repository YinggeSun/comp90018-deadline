package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.GameState

/** Pure-Kotlin session API; win/loss evaluation remains deferred. */
interface GameEngine {
    /** Current read-only snapshot; only the engine can replace its state. */
    val state: GameState

    /** Appends a selectable tile and resolves its triple; unavailable IDs or a full tray are no-ops. */
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
