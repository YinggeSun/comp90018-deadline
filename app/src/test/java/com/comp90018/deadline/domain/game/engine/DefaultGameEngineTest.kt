package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.TrayState
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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

        engine.selectTile(initialState.board.tiles.first().id)
        assertSame(initialState, engine.state)
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

        // No public action changes state yet. Seed runtime state through reflection
        // to exercise a real reset without adding a production mutation API.
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
}
