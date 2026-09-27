package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.model.GameState

/** UI snapshot derived from the domain engine plus sensor-only presentation state. */
data class GameUiState(
    val gameState: GameState = GameState(),
    val peekAmount: Float = 0f,
)
