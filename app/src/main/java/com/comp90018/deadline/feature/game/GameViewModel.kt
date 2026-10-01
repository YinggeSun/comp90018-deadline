package com.comp90018.deadline.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns one game session. Forwards taps to the [GameEngine] and republishes
 * its state as [GameUiState]; all game rules stay in the engine.
 */
class GameViewModel(
    levelId: String,
    findLevel: (String) -> Level? = ::findFixedLevel,
    createEngine: (Level) -> GameEngine = { DefaultGameEngine(it) }
) : ViewModel() {

    private val level = findLevel(levelId)
    private val engine = level?.let(createEngine)

    private val _uiState = MutableStateFlow(
        if (level == null || engine == null) {
            GameUiState(levelNotFound = true)
        } else {
            initialUiState(level, engine)
        }
    )
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun onEvent(event: GameUiEvent) {
        val engine = engine ?: return
        when (event) {
            is GameUiEvent.TileTapped -> engine.selectTile(event.tileId)
        }
        _uiState.value = _uiState.value.withEngineState(engine)
    }

    private fun initialUiState(level: Level, engine: GameEngine): GameUiState {
        val tiles = engine.state.board.tiles
        return GameUiState(
            levelName = level.name,
            boardRows = tiles.maxOfOrNull { it.position.row + TILE_SPAN } ?: 0,
            boardColumns = tiles.maxOfOrNull { it.position.column + TILE_SPAN } ?: 0
        ).withEngineState(engine)
    }

    private fun GameUiState.withEngineState(engine: GameEngine): GameUiState {
        val state: GameState = engine.state
        return copy(
            boardTiles = state.board.tiles
                .sortedWith(compareBy({ it.position.layer }, { it.position.row }, { it.position.column }))
                .map { it.toUiModel(isSelectable = engine.isTileSelectable(it.id)) },
            trayTiles = state.taskTray.tiles.map { it.toUiModel(isSelectable = false) },
            trayCapacity = state.taskTray.capacity,
            status = state.status
        )
    }

    private fun Tile.toUiModel(isSelectable: Boolean) = TileUiModel(
        id = id,
        type = type,
        row = position.row,
        column = position.column,
        layer = position.layer,
        isSelectable = isSelectable
    )

    companion object {
        /** A tile covers this many logical units in each direction (see TilePosition). */
        const val TILE_SPAN = 2

        fun factory(levelId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(levelId) }
        }
    }
}

private fun findFixedLevel(levelId: String): Level? =
    (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL).find { it.id == levelId }
