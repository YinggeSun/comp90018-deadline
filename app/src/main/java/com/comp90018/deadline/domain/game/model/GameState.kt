package com.comp90018.deadline.domain.game.model

/**
 * Core session snapshot, independent of rendering and Android lifecycle state.
 * Tile IDs must be unique across board and tray; future state-creation or
 * game-engine logic is responsible for maintaining this domain expectation.
 * [status] is explicit: an empty initial board is not automatically a win.
 *
 * [stress] is the Stress System value for this session, in stress points. The engine always
 * stores a value within the configured range, but that bound belongs to StressConfig rather
 * than to this model, so state restored under a narrower configuration is repaired by
 * StressManager.clamp instead of being rejected here.
 */
data class GameState(
    val board: Board = Board(),
    val taskTray: TrayState = TrayState(),
    val status: GameStatus = GameStatus.RUNNING,
    val stress: Int = 0
)
