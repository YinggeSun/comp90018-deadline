package com.comp90018.deadline.feature.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager

/**
 * Integration point between the real GameEngine (#55-#60) and Person 3 sensor features.
 *
 * This class intentionally routes every gameplay mutation through [GameEngine]. Sensors
 * never edit Board/GameState directly, which keeps overlap, tray, matching and win/loss
 * rules consistent with the existing engine implementation.
 */
class GameViewModel(
    private val engine: GameEngine,
    private val haptics: HapticFeedbackManager? = null,
) : GameSensorActions {

    var uiState by mutableStateOf(
        GameUiState(gameState = engine.state)
    )
        private set

    /** Called by the Game UI when the player selects a tile. */
    fun selectTile(tileId: String) {
        val before = engine.state
        engine.selectTile(tileId)
        val after = engine.state
        if (after === before) return

        uiState = uiState.copy(gameState = after)

        val successfulSelection = after.board.tiles.size == before.board.tiles.size - 1
        if (!successfulSelection) return

        // Prefer the most meaningful feedback when several outcomes happen together.
        val feedback = when {
            after.status == GameStatus.WON && before.status != GameStatus.WON -> GameHaptic.SUCCESS
            after.status == GameStatus.LOST && before.status != GameStatus.LOST -> GameHaptic.FAILURE
            after.taskTray.tiles.size < before.taskTray.tiles.size + 1 -> GameHaptic.MATCH
            else -> GameHaptic.TILE_SELECT
        }
        haptics?.perform(feedback)
    }

    fun restart() {
        engine.restart()
        uiState = uiState.copy(
            gameState = engine.state,
            peekAmount = 0f,
        )
    }

    fun undo() {
        val before = engine.state
        engine.undo()
        if (engine.state !== before) {
            uiState = uiState.copy(gameState = engine.state)
        }
    }

    /** #25: accepted shake requests are routed to the real GameEngine shuffle API. */
    override fun onShuffleRequested(): Boolean {
        val before = engine.state
        val accepted = before.status == GameStatus.RUNNING && before.board.tiles.size >= 2
        if (!accepted) return false

        engine.shuffle()
        uiState = uiState.copy(gameState = engine.state)
        return true
    }

    /** #27: tilt changes rendering state only and never mutates the domain board. */
    override fun onPeekChanged(amount: Float) {
        uiState = uiState.copy(peekAmount = amount.coerceIn(0f, 1f))
    }
}
