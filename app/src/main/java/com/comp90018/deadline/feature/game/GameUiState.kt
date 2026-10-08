package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.game.stress.StressConfig

/**
 * One tile as the board draws it. [row], [column] and [layer] are the logical
 * TilePosition coordinates; [isSelectable] comes from the game engine.
 */
data class TileUiModel(
    val id: String,
    val type: TileType,
    val row: Int,
    val column: Int,
    val layer: Int,
    val isSelectable: Boolean,
)

/**
 * Everything the Game screen renders. [boardTiles] are ordered bottom layer
 * first so later tiles are drawn on top. [boardRows] and [boardColumns] are
 * the logical size of the starting board, so the layout does not shift as
 * tiles are removed.
 *
 * [stress] and [stressMaximum] describe the Stress System gauge, and
 * [isHighStress] is the warning state the engine's configuration defines, so
 * the HUD never has to re-derive the threshold.
 *
 * [previousBestMillis] is this level's Personal Best from before the current run. It is
 * read in the same update that sets [status] to WON, before the new time is saved, so the
 * Result screen can tell whether this run set a new record without waiting for the save.
 * It is null while the game is running and when the level had no best yet.
 *
 * Elapsed time is not part of this state; it is published separately by
 * [GameViewModel.elapsedSeconds] so the whole screen does not recompose every
 * second.
 */
data class GameUiState(
    val levelName: String = "",
    val boardTiles: List<TileUiModel> = emptyList(),
    val boardRows: Int = 0,
    val boardColumns: Int = 0,
    val trayTiles: List<TileUiModel> = emptyList(),
    val trayCapacity: Int = 0,
    val status: GameStatus = GameStatus.RUNNING,
    val levelNotFound: Boolean = false,
    val peekAmount: Float = 0f,
    val stress: Int = 0,
    val stressMaximum: Int = StressConfig.DEFAULT_MAXIMUM,
    val isHighStress: Boolean = false,
    val canUndo: Boolean = false,
    val previousBestMillis: Long? = null,
)
