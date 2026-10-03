package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.engine.CompletionTimer
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.domain.level.model.FixedLevels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    private var nowNanos = 0L
    private fun advanceSeconds(seconds: Long) {
        nowNanos += seconds * 1_000_000_000L
    }

    private fun timedViewModel(levelId: String) =
        GameViewModel(levelId, timer = CompletionTimer { nowNanos })

    @Test
    fun timerRunsWhileGameIsRunning() {
        val viewModel = timedViewModel(FixedLevels.LEVEL_3.id)

        advanceSeconds(5)

        assertEquals(5_000L, viewModel.elapsedMillis)
        assertEquals(5L, runBlocking { viewModel.elapsedSeconds.first() })
    }

    @Test
    fun timerStopsWhenGameIsWon() {
        val level = FixedLevels.SAMPLE_LEVEL
        val viewModel = timedViewModel(level.id)

        advanceSeconds(3)
        level.board.tiles.forEach { viewModel.onEvent(GameUiEvent.TileTapped(it.id)) }
        advanceSeconds(10)

        assertEquals(GameStatus.WON, viewModel.uiState.value.status)
        assertEquals(3_000L, viewModel.elapsedMillis)
    }

    @Test
    fun restartResetsBoardTrayAndTimer() {
        val viewModel = timedViewModel(FixedLevels.LEVEL_3.id)
        advanceSeconds(7)
        viewModel.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))

        viewModel.onEvent(GameUiEvent.RestartClicked)
        val state = viewModel.uiState.value

        assertEquals(FixedLevels.LEVEL_3.board.tiles.size, state.boardTiles.size)
        assertTrue(state.trayTiles.isEmpty())
        assertEquals(0L, viewModel.elapsedMillis)
        advanceSeconds(2)
        assertEquals(2_000L, viewModel.elapsedMillis)
    }

    @Test
    fun undoReturnsLastTileToBoard() {
        val viewModel = GameViewModel(FixedLevels.LEVEL_3.id)
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.onEvent(GameUiEvent.UndoClicked)
        val state = viewModel.uiState.value

        assertTrue(state.boardTiles.any { it.id == "level_3_laptop_1" })
        assertTrue(state.trayTiles.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun undoIsUnavailableAfterMatch() {
        val viewModel = GameViewModel(FixedLevels.LEVEL_3.id)

        listOf("level_3_laptop_1", "level_3_laptop_2", "level_3_laptop_3")
            .forEach { viewModel.onEvent(GameUiEvent.TileTapped(it)) }

        assertTrue(viewModel.uiState.value.trayTiles.isEmpty())
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun stressStartsAtZeroWithConfiguredMaximum() {
        val state = GameViewModel(FixedLevels.LEVEL_3.id).uiState.value

        assertEquals(0, state.stress)
        assertEquals(StressConfig.DEFAULT_MAXIMUM, state.maxStress)
        assertFalse(state.isHighStress)
    }

    @Test
    fun trayCapacityComesFromGameState() {
        assertEquals(7, GameViewModel(FixedLevels.LEVEL_3.id).uiState.value.trayCapacity)
    }
}
