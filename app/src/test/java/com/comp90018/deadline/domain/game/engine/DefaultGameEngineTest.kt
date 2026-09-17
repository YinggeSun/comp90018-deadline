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

        // Seed terminal status and custom capacity through reflection to exercise their
        // reset without adding a production mutation API.
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
    fun selectionMovesExactTilePreservesSnapshotsAndStatusAndRejectsUnavailableIds() {
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
        assertTrue(initial.taskTray.tiles.isEmpty())
        assertEquals(listOf(initial.board.tiles[1]), afterRemoval.taskTray.tiles)
        assertSame(initial.board.tiles[1], afterRemoval.taskTray.tiles.single())
        assertEquals(initial.status, afterRemoval.status)
        assertTrue(engine.isTileSelectable("lower"))
        assertFalse(engine.isTileSelectable("upper"))
        engine.selectTile("upper")
        assertSame(afterRemoval, engine.state)
        engine.selectTile("lower")
        engine.selectTile("other")
        assertTrue(engine.state.board.tiles.isEmpty())
        assertEquals(GameStatus.RUNNING, engine.state.status)
        assertEquals(listOf("upper", "lower", "other"), engine.state.taskTray.tiles.map { it.id })
        assertEquals(listOf("upper"), afterRemoval.taskTray.tiles.map { it.id })
        assertEquals(listOf("lower", "other"), afterRemoval.board.tiles.map { it.id })
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

    @Test
    fun trayPreservesSelectionOrderWithoutMatchingIdenticalTypes() {
        val a = tile("z", 0, 8)
        val b = tile("a", 0, 4)
        val c = tile("m", 0, 0)
        val engine = engineWith(c, a, b)

        for (selected in listOf(a, b, c)) engine.selectTile(selected.id)

        assertEquals(listOf(a, b, c), engine.state.taskTray.tiles)
        assertEquals(GameStatus.RUNNING, engine.state.status)
    }

    @Test
    fun sevenTilesFillTrayAndEighthSelectionLeavesBoardGraphTrayAndStatusUnchanged() {
        val fillers = (1..TrayState.DEFAULT_CAPACITY).map { tile("fill-$it", 0, it * 4) }
        val upper = tile("eighth", 1)
        val lower = tile("lower", 0)
        val engine = engineWith(*(fillers + upper + lower).toTypedArray())
        for (selected in fillers) engine.selectTile(selected.id)

        val fullState = engine.state
        assertEquals(7, fullState.taskTray.tiles.size)
        assertTrue(fullState.taskTray.isFull)
        assertEquals(fillers, fullState.taskTray.tiles)
        assertEquals(GameStatus.RUNNING, fullState.status)
        assertTrue(engine.isTileSelectable(upper.id))
        assertFalse(engine.isTileSelectable(lower.id))

        repeat(2) {
            engine.selectTile(upper.id)
            assertSame(fullState, engine.state)
            assertEquals(listOf(upper, lower), engine.state.board.tiles)
            assertEquals(fillers, engine.state.taskTray.tiles)
            assertTrue(engine.isTileSelectable(upper.id))
            // The rejected upper tile is the sole blocker: any decrement would unlock lower.
            assertFalse(engine.isTileSelectable(lower.id))
            assertEquals(GameStatus.RUNNING, engine.state.status)
        }
    }

    @Test
    fun selectionHonorsConfiguredCapacityAndPreservesExistingStatus() {
        val first = tile("first", 0)
        val second = tile("second", 0, 4)
        val engine = engineWith(first, second)
        val stateField = DefaultGameEngine::class.java.getDeclaredField("currentState")
        stateField.isAccessible = true
        for (status in GameStatus.values()) {
            engine.restart()
            stateField.set(engine, engine.state.copy(taskTray = TrayState(capacity = 1), status = status))

            engine.selectTile(first.id)
            val fullState = engine.state
            assertEquals(listOf(first), fullState.taskTray.tiles)
            assertEquals(1, fullState.taskTray.capacity)
            assertTrue(fullState.taskTray.isFull)
            assertEquals(status, fullState.status)

            engine.selectTile(second.id)
            assertSame(fullState, engine.state)
            assertTrue(engine.isTileSelectable(second.id))
        }
    }

    private fun tile(id: String, layer: Int, column: Int = 0) =
        Tile(id, TileType.DEFAULT, TilePosition(0, column, layer))

    private fun engineWith(vararg tiles: Tile): GameEngine = DefaultGameEngine(
        FixedLevels.SAMPLE_LEVEL.copy(board = Board(tiles.toList()))
    )
}
