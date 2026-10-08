package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.shuffle.BoardShuffler
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.sensor.tilt.TiltProcessor
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SensorIntegrationTest {
    @Test fun shufflePreservesTrayGeometryAndAvailabilityAndInvalidatesOldUndo() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_3, boardShuffler = BoardShuffler(Random(42)))
        engine.selectTile(engine.state.board.tiles.first { engine.isTileSelectable(it.id) }.id)
        val before = engine.state
        val selectable = before.board.tiles.associate { it.id to engine.isTileSelectable(it.id) }
        repeat(20) { if (engine.state == before) engine.shuffle() }
        val shuffled = engine.state
        assertNotEquals(before.board, shuffled.board)
        assertEquals(before.taskTray, shuffled.taskTray)
        assertEquals(before.board.tiles.map { it.id to it.position }, shuffled.board.tiles.map { it.id to it.position })
        assertEquals(before.board.tiles.groupingBy { it.type }.eachCount(), shuffled.board.tiles.groupingBy { it.type }.eachCount())
        assertEquals(selectable, shuffled.board.tiles.associate { it.id to engine.isTileSelectable(it.id) })
        engine.undo()
        assertEquals(shuffled, engine.state)
        engine.selectTile(engine.state.board.tiles.first { engine.isTileSelectable(it.id) }.id)
        engine.undo()
        assertEquals(shuffled, engine.state)
        engine.restart()
        assertEquals(FixedLevels.LEVEL_3.board, engine.state.board)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
    }

    @Test fun viewModelPublishesShuffleAndKeepsBoardDimensions() {
        val vm = GameViewModel(FixedLevels.LEVEL_3.id, createEngine = {
            DefaultGameEngine(it, boardShuffler = BoardShuffler(Random(42)))
        })
        val before = vm.uiState.value
        assertTrue(vm.onShuffleRequested())
        assertNotEquals(before.boardTiles, vm.uiState.value.boardTiles)
        assertEquals(before.boardRows, vm.uiState.value.boardRows)
        assertEquals(before.boardColumns, vm.uiState.value.boardColumns)
    }

    @Test fun noOpAndMissingLevelDoNotReportAcceptedShuffle() {
        assertFalse(GameViewModel(FixedLevels.SAMPLE_LEVEL.id).onShuffleRequested())
        assertFalse(GameViewModel("missing").onShuffleRequested())
    }

    @Test fun invalidTiltDoesNotPoisonSubsequentReadingsOrMutateBoard() {
        val processor = TiltProcessor()
        val vm = GameViewModel(FixedLevels.LEVEL_3.id)
        val board = vm.uiState.value.boardTiles
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertTrue(processor.processPitch(it).isFinite())
            vm.onPeekChanged(it)
            assertEquals(0f, vm.uiState.value.peekAmount)
        }
        repeat(40) { vm.onPeekChanged(processor.processPitch(40f)) }
        assertEquals(1f, vm.uiState.value.peekAmount)
        assertEquals(board, vm.uiState.value.boardTiles)
        vm.restart()
        assertEquals(0f, vm.uiState.value.peekAmount)
    }
}
