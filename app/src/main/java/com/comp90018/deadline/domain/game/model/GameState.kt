package com.comp90018.deadline.domain.game.model

/**
 * Core session snapshot, independent of rendering and Android lifecycle state.
 * Tile IDs must be unique across board and tray; future state-creation or
 * game-engine logic is responsible for maintaining this domain expectation.
 * [status] is explicit: an empty initial board is not automatically a win.
 */
data class GameState(
    val board: Board = Board(),
    val taskTray: TrayState = TrayState(),
    val status: GameStatus = GameStatus.RUNNING
)
