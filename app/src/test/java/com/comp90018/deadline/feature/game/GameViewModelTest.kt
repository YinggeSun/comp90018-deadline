package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelTest {

    private fun GameViewModel.tile(id: String) = uiState.value.boardTiles.single { it.id == id }

    @Test
    fun loadsLevelIntoUiState() {
        val level = FixedLevels.LEVEL_3
        val viewModel = GameViewModel(level.id)
        val state = viewModel.uiState.value

        assertFalse(state.levelNotFound)
        assertEquals(level.name, state.levelName)
        assertEquals(level.board.tiles.map { it.id }.toSet(), state.boardTiles.map { it.id }.toSet())
        assertEquals(GameStatus.RUNNING, state.status)
        assertTrue(state.trayTiles.isEmpty())
    }

    @Test
    fun boardSizeCoversEveryTileFootprint() {
        val state = GameViewModel(FixedLevels.LEVEL_3.id).uiState.value

        // Furthest tiles are at row 3 and column 4; each spans two units.
        assertEquals(5, state.boardRows)
        assertEquals(6, state.boardColumns)
    }

    @Test
    fun tilesAreOrderedBottomLayerFirst() {
        val layers = GameViewModel(FixedLevels.LEVEL_3.id).uiState.value.boardTiles.map { it.layer }

        assertEquals(layers.sorted(), layers)
    }

    @Test
    fun selectableStateComesFromEngine() {
        val viewModel = GameViewModel(FixedLevels.LEVEL_3.id)

        assertTrue(viewModel.tile("level_3_laptop_1").isSelectable)
        assertFalse(viewModel.tile("level_3_book_1").isSelectable)
    }

    @Test
    fun tappingSelectableTileMovesItToTray() {
        val viewModel = GameViewModel(FixedLevels.LEVEL_3.id)

        viewModel.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))
        val state = viewModel.uiState.value

        assertFalse(state.boardTiles.any { it.id == "level_3_laptop_1" })
        assertEquals(listOf("level_3_laptop_1"), state.trayTiles.map { it.id })
    }

    @Test
    fun tappingCoveredTileChangesNothing() {
        val viewModel = GameViewModel(FixedLevels.LEVEL_3.id)
        val before = viewModel.uiState.value

        viewModel.onEvent(GameUiEvent.TileTapped("level_3_book_1"))

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun uncoveringUpdatesSelectableState() {
        val viewModel = GameViewModel(FixedLevels.LEVEL_3.id)

        // laptop_1 is the only tile covering book_1.
        viewModel.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))

        assertTrue(viewModel.tile("level_3_book_1").isSelectable)
    }

    @Test
    fun clearingBoardWinsGame() {
        val level = FixedLevels.SAMPLE_LEVEL
        val viewModel = GameViewModel(level.id)

        level.board.tiles.forEach { viewModel.onEvent(GameUiEvent.TileTapped(it.id)) }
        val state = viewModel.uiState.value

        assertEquals(GameStatus.WON, state.status)
        assertTrue(state.boardTiles.isEmpty())
        assertTrue(state.trayTiles.isEmpty())
    }

    @Test
    fun unknownLevelIsReported() {
        val viewModel = GameViewModel("missing_level")

        assertTrue(viewModel.uiState.value.levelNotFound)
        viewModel.onEvent(GameUiEvent.TileTapped("anything"))
        assertTrue(viewModel.uiState.value.boardTiles.isEmpty())
    }
}
