package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.TrayState
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGameEngineTest {
    @Test
    fun createsReadableInitialStateFromLevel() {
        val level = FixedLevels.SAMPLE_LEVEL
        val engine: GameEngine = DefaultGameEngine(level)

        assertEquals(GameState(board = level.board), engine.state)
        assertEquals(level.board.tiles, engine.state.board.tiles)
        assertEquals(emptyList<Any>(), engine.state.taskTray.tiles)
        assertEquals(TrayState.DEFAULT_CAPACITY, engine.state.taskTray.capacity)
        assertEquals(GameStatus.RUNNING, engine.state.status)
    }

    @Test
    fun stateHasNoPublicSetter() {
        assertFalse(GameEngine::class.java.methods.any { it.name == "setState" })
        assertFalse(DefaultGameEngine::class.java.methods.any { it.name == "setState" })
    }

    @Test
    fun placeholderActionsLeaveStateUnchanged() {
        val engine: GameEngine = DefaultGameEngine(FixedLevels.SAMPLE_LEVEL)
        val initialState = engine.state

        engine.selectTile("unknown-tile")
        assertSame(initialState, engine.state)
        engine.undo()
        assertSame(initialState, engine.state)
        engine.shuffle()
        assertSame(initialState, engine.state)
    }

    @Test
    fun restartRestoresBoardTrayAndStatusAfterRuntimeStateReplacement() {
        val level = FixedLevels.SAMPLE_LEVEL
        val engine = DefaultGameEngine(level)
        val expectedState = GameState(board = level.board)

        // Tray insertion and status transitions are deferred. Seed them through reflection
        // to exercise their reset without adding a production mutation API.
        val stateField = DefaultGameEngine::class.java.getDeclaredField("currentState")
        stateField.isAccessible = true
        for (status in listOf(GameStatus.WON, GameStatus.LOST)) {
            val runtimeState = GameState(
                board = Board(level.board.tiles.drop(1)),
                taskTray = TrayState(listOf(level.board.tiles.first()), capacity = 1),
                status = status
            )
            stateField.set(engine, runtimeState)
            assertEquals(runtimeState, engine.state)

            engine.restart()

            assertEquals(expectedState, engine.state)
        }
    }

    @Test
    fun restartIsRepeatableFromInitialState() {
        val engine: GameEngine = DefaultGameEngine(FixedLevels.SAMPLE_LEVEL)
        val expectedState = engine.state

        repeat(2) {
            engine.restart()
            assertEquals(expectedState, engine.state)
        }
    }

    @Test
    fun selectionPreservesOldSnapshotTrayAndStatusAndRejectsUnavailableIds() {
        val engine = engineWith(tile("lower", 0), tile("upper", 1), tile("other", 0, 8))
        val initial = engine.state
        assertFalse(engine.isTileSelectable("lower"))
        assertFalse(engine.isTileSelectable("unknown"))
        engine.selectTile("lower")
        engine.selectTile("unknown")
        assertSame(initial, engine.state)

        engine.selectTile("upper")
        val afterRemoval = engine.state
        assertEquals(listOf("lower", "other"), afterRemoval.board.tiles.map { it.id })
        assertEquals(3, initial.board.tiles.size)
        assertSame(initial.taskTray, afterRemoval.taskTray)
        assertEquals(initial.status, afterRemoval.status)
        assertTrue(engine.isTileSelectable("lower"))
        assertFalse(engine.isTileSelectable("upper"))
        engine.selectTile("upper")
        assertSame(afterRemoval, engine.state)
        engine.selectTile("lower")
        engine.selectTile("other")
        assertTrue(engine.state.board.tiles.isEmpty())
        assertEquals(GameStatus.RUNNING, engine.state.status)
        assertSame(initial.taskTray, engine.state.taskTray)
    }

    @Test
    fun restartRestoresMultipleBlockersAndAvailabilityAcrossRepeatedRuns() {
        val engine = engineWith(tile("lower", 0), tile("a", 1), tile("b", 1))
        val initial = engine.state
        repeat(2) {
            assertFalse(engine.isTileSelectable("lower"))
            assertTrue(engine.isTileSelectable("a"))
            assertTrue(engine.isTileSelectable("b"))
            engine.selectTile("a")
            assertFalse(engine.isTileSelectable("lower"))
            engine.selectTile("b")
            assertTrue(engine.isTileSelectable("lower"))
            engine.selectTile("lower")
            engine.restart()
            assertEquals(initial, engine.state)
            assertTrue(engine.state.taskTray.tiles.isEmpty())
            assertEquals(GameStatus.RUNNING, engine.state.status)
            assertFalse(engine.isTileSelectable("lower"))
        }
    }

    private fun tile(id: String, layer: Int, column: Int = 0) =
        Tile(id, TileType.DEFAULT, TilePosition(0, column, layer))

    private fun engineWith(vararg tiles: Tile): GameEngine = DefaultGameEngine(
        FixedLevels.SAMPLE_LEVEL.copy(board = Board(tiles.toList()))
    )
}
