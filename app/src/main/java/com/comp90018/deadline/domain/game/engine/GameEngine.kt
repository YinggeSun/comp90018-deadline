package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.stress.StressConfig

/** Pure-Kotlin session API with deterministic win/loss evaluation after matching. */
interface GameEngine {
    /** Current read-only snapshot; only the engine can replace its state. */
    val state: GameState

    /**
     * Tuning this engine applies to [GameState.stress]. Exposed because a stress gauge needs
     * the range and the High Stress threshold, and must not re-declare them.
     */
    val stressConfig: StressConfig
        get() = StressConfig()

    /**
     * Resolves selection, matching, stress, then win/loss; terminal games, unavailable IDs
     * and full trays are no-ops that leave [GameState.stress] unchanged.
     */
    fun selectTile(tileId: String)

    /** True only while running, for a tile still on the board with no active covering tiles. */
    fun isTileSelectable(tileId: String): Boolean

    /** Restores the latest retained selection since the last match while running; unavailable undo is a no-op. */
    fun undo()

    /**
     * Shuffles matchable contents across the remaining board slots while preserving
     * board geometry, tray contents and availability relationships. Terminal games
     * ignore shuffle requests.
     */
    fun shuffle()

    /**
     * Restores the initial board and availability, empty tray, running status and starting
     * stress; clears undo history.
     */
    fun restart()
}
