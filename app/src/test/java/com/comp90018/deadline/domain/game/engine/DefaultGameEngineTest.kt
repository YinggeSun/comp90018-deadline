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
        assertEquals(GameStatus.WON, engine.state.status)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
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
            assertTrue(engine.state.board.tiles.isEmpty())
            assertTrue(engine.state.taskTray.tiles.isEmpty())
            engine.restart()
            assertEquals(initial, engine.state)
            assertTrue(engine.state.taskTray.tiles.isEmpty())
            assertEquals(GameStatus.RUNNING, engine.state.status)
            assertFalse(engine.isTileSelectable("lower"))
        }
    }

    @Test
    fun thirdIdenticalTileMatchesImmediatelyPreservingEarlierSnapshots() {
        val a = tile("z", 0, 8)
        val b = tile("a", 0, 4)
        val c = tile("m", 0, 0)
        val d = tile("next", 0, 12)
        val engine = engineWith(c, a, d, b)

        engine.selectTile(a.id)
        val afterFirst = engine.state
        assertEquals(listOf(a), afterFirst.taskTray.tiles)
        assertSame(a, afterFirst.taskTray.tiles.single())
        engine.selectTile(b.id)
        val afterSecond = engine.state
        val oldTray = afterSecond.taskTray
        assertEquals(listOf(a, b), oldTray.tiles)
        assertSame(b, oldTray.tiles[1])

        engine.selectTile(c.id)
        val afterMatch = engine.state
        assertTrue(afterMatch.taskTray.tiles.isEmpty())
        assertEquals(listOf(d), afterMatch.board.tiles)
        for (matched in listOf(a, b, c)) assertFalse(engine.isTileSelectable(matched.id))
        assertTrue(engine.isTileSelectable(d.id))
        assertEquals(listOf(a), afterFirst.taskTray.tiles)
        assertEquals(listOf(a, b), oldTray.tiles)
        assertEquals(listOf(c, d), afterSecond.board.tiles)
        assertEquals(GameStatus.RUNNING, engine.state.status)

        for (invalid in listOf(a.id, b.id, c.id, "unknown")) {
            engine.selectTile(invalid)
            assertSame(afterMatch, engine.state)
        }
        engine.selectTile(d.id)
        assertEquals(listOf(d), engine.state.taskTray.tiles)
        assertTrue(afterMatch.taskTray.tiles.isEmpty())
    }

    @Test
    fun fullSevenSlotTrayRejectsSelectionWithoutChangingBoardGraphTrayOrStatus() {
        val fillers = (1..TrayState.DEFAULT_CAPACITY).map { tile("fill-$it", 0, it * 4) }
        val upper = tile("eighth", 1)
        val lower = tile("lower", 0)
        val engine = engineWith(upper, lower)
        // DEFAULT is the only type, so automatic matching prevents filling seven slots
        // through selections. Seed this capacity boundary without changing the model.
        val stateField = DefaultGameEngine::class.java.getDeclaredField("currentState")
        stateField.isAccessible = true
        stateField.set(engine, engine.state.copy(taskTray = TrayState(fillers)))

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
    fun coveredAndUnknownSelectionsCannotCompleteMatchButValidSelectionUnlocksLowerTile() {
        val a = tile("a", 0, 4)
        val b = tile("b", 0, 8)
        val upper = tile("upper", 1)
        val lower = tile("lower", 0)
        val engine = engineWith(a, b, upper, lower)
        engine.selectTile(a.id)
        engine.selectTile(b.id)
        val beforeMatch = engine.state

        for (invalid in listOf(lower.id, "unknown", a.id)) {
            engine.selectTile(invalid)
            assertSame(beforeMatch, engine.state)
            assertFalse(engine.isTileSelectable(lower.id))
        }
        engine.selectTile(upper.id)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertEquals(listOf(lower), engine.state.board.tiles)
        assertTrue(engine.isTileSelectable(lower.id))
        engine.selectTile(lower.id)
        assertEquals(listOf(lower), engine.state.taskTray.tiles)
    }

    @Test
    fun fullTrayRejectsEvenATileThatWouldCompleteATriple() {
        val engine = DefaultGameEngine(FixedLevels.SAMPLE_LEVEL)
        val tiles = engine.state.board.tiles
        val stateField = DefaultGameEngine::class.java.getDeclaredField("currentState")
        stateField.isAccessible = true
        stateField.set(engine, engine.state.copy(taskTray = TrayState(capacity = 2)))
        engine.selectTile(tiles[0].id)
        engine.selectTile(tiles[1].id)
        val fullState = engine.state

        engine.selectTile(tiles[2].id)

        assertSame(fullState, engine.state)
        assertEquals(tiles.take(2), engine.state.taskTray.tiles)
        assertEquals(listOf(tiles[2]), engine.state.board.tiles)
        assertFalse(engine.isTileSelectable(tiles[2].id))
        assertEquals(GameStatus.LOST, engine.state.status)
    }

    @Test
    fun singleTileWinsEvenWithFullFinalTray() {
        for (capacity in listOf(1, 7)) {
            val last = tile("last", 0)
            val engine = engineWith(last)
            setTestState(engine, engine.state.copy(taskTray = TrayState(capacity = capacity)))
            val initial = engine.state
            engine.selectTile(last.id)
            assertTrue(engine.state.board.tiles.isEmpty())
            assertEquals(listOf(last), engine.state.taskTray.tiles)
            assertEquals(capacity == 1, engine.state.taskTray.isFull)
            assertEquals(GameStatus.WON, engine.state.status)
            assertEquals(GameStatus.RUNNING, initial.status)
            assertEquals(listOf(last), initial.board.tiles)
            assertTrue(initial.taskTray.tiles.isEmpty())
        }
    }

    @Test
    fun matchingAtCapacityPrecedesStatusEvaluation() {
        for (remaining in listOf(false, true)) {
            val tiles = (0..3).map { tile("tile-$it", 0, it * 4) }
            val engine = engineWith(*tiles.take(if (remaining) 4 else 3).toTypedArray())
            setTestState(engine, engine.state.copy(taskTray = TrayState(capacity = 3)))
            engine.selectTile(tiles[0].id)
            assertEquals(GameStatus.RUNNING, engine.state.status)
            engine.selectTile(tiles[1].id)
            val beforeMatch = engine.state
            assertEquals(GameStatus.RUNNING, beforeMatch.status)
            engine.selectTile(tiles[2].id)
            assertTrue(engine.state.taskTray.tiles.isEmpty())
            assertEquals(3, engine.state.taskTray.capacity)
            assertEquals(if (remaining) GameStatus.RUNNING else GameStatus.WON, engine.state.status)
            assertEquals(tiles.take(2), beforeMatch.taskTray.tiles)
            assertEquals(tiles.drop(2).take(if (remaining) 2 else 1), beforeMatch.board.tiles)
            assertEquals(GameStatus.RUNNING, beforeMatch.status)
        }
    }

    @Test
    fun lossFreezesStateAndGraphAndRestartRestoresEveryBlocker() {
        val engine = engineWith(tile("lower", 0), tile("a", 1), tile("b", 1))
        val initial = engine.state
        repeat(2) {
            setTestState(engine, engine.state.copy(taskTray = TrayState(capacity = 1)))
            val beforeLoss = engine.state
            engine.selectTile("a")
            assertEquals(GameStatus.LOST, engine.state.status)
            assertTrue(engine.state.taskTray.isFull)
            assertEquals(listOf("lower", "b"), engine.state.board.tiles.map { it.id })
            assertEquals(GameStatus.RUNNING, beforeLoss.status)
            assertEquals(initial.board, beforeLoss.board)
            assertTrue(beforeLoss.taskTray.tiles.isEmpty())
            assertTerminalFrozen(engine, listOf("lower", "a", "b", "unknown"))
            engine.restart()
            assertSame(initial, engine.state)
            assertEquals(mapOf("lower" to 2, "a" to 0, "b" to 0), blockerCounts(engine))
            assertFalse(engine.isTileSelectable("lower"))
            assertTrue(engine.isTileSelectable("a"))
            assertTrue(engine.isTileSelectable("b"))
        }
    }

    @Test
    fun winFreezesStateAndRestartRestoresGraphAndGameplay() {
        val engine = engineWith(tile("lower", 0), tile("a", 1), tile("b", 1))
        val initial = engine.state
        repeat(2) {
            for (id in listOf("a", "b", "lower")) engine.selectTile(id)
            assertEquals(GameStatus.WON, engine.state.status)
            assertTerminalFrozen(engine, listOf("lower", "a", "b", "unknown"))
            engine.restart()
            assertSame(initial, engine.state)
            assertEquals(mapOf("lower" to 2, "a" to 0, "b" to 0), blockerCounts(engine))
            assertFalse(engine.isTileSelectable("lower"))
            assertTrue(engine.isTileSelectable("a"))
            assertTrue(engine.isTileSelectable("b"))
        }
    }

    @Test
    fun terminalStatusesRejectGeometricallySelectableTilesWithoutGraphMutation() {
        // A won board normally has no tiles; seed one to explicitly test the status guard.
        for (status in listOf(GameStatus.WON, GameStatus.LOST)) {
            val engine = engineWith(tile("lower", 0), tile("upper", 1))
            setTestState(engine, engine.state.copy(status = status))
            assertTerminalFrozen(engine, listOf("upper", "lower", "unknown"))
        }
    }

    @Test
    fun rejectedSelectionOnEmptyInitialBoardDoesNotEvaluateWin() {
        val engine = engineWith()
        val initial = engine.state
        engine.selectTile("unknown")
        assertSame(initial, engine.state)
        assertEquals(GameStatus.RUNNING, engine.state.status)
    }

    @Test
    fun restartMatchesFreshEngineAcrossGameplayStagesAndRepeatedCycles() {
        val lower = tile("lower", 0)
        val a = tile("a", 1)
        val b = tile("b", 1)
        val other = tile("other", 0, 8)
        val level = FixedLevels.SAMPLE_LEVEL.copy(board = Board(listOf(lower, a, b, other)))
        val order = listOf(a.id, b.id, lower.id, other.id)
        val ids = level.board.tiles.map { it.id } + "unknown"

        // Untouched, partial selection, overlap unlock, automatic match, win, and loss.
        for (stage in listOf("initial", "selection", "unlock", "match", "won", "lost")) {
            val engine = DefaultGameEngine(level)
            repeat(2) {
                if (stage == "lost") {
                    // DEFAULT alone cannot fill seven slots through normal matching.
                    setTestState(engine, engine.state.copy(taskTray = TrayState(capacity = 1)))
                }
                val selections = when (stage) {
                    "initial" -> 0
                    "selection", "lost" -> 1
                    "unlock" -> 2
                    "match" -> 3
                    else -> 4
                }
                for (id in order.take(selections)) engine.selectTile(id)
                val beforeRestart = engine.state
                val expectedBeforeRestart = beforeRestart.copy(
                    board = Board(beforeRestart.board.tiles.toList()),
                    taskTray = beforeRestart.taskTray.copy(tiles = beforeRestart.taskTray.tiles.toList())
                )
                assertEquals(4 - selections, beforeRestart.board.tiles.size)
                assertEquals(selections % 3, beforeRestart.taskTray.tiles.size)
                assertEquals(
                    when (stage) {
                        "won" -> GameStatus.WON
                        "lost" -> GameStatus.LOST
                        else -> GameStatus.RUNNING
                    },
                    beforeRestart.status
                )
                if (stage == "unlock") assertTrue(engine.isTileSelectable(lower.id))
                if (stage == "match") assertEquals(listOf(other), beforeRestart.board.tiles)

                val fresh = DefaultGameEngine(level)
                repeat(3) {
                    engine.restart()
                    assertEquivalentGameplay(fresh, engine, ids)
                    assertEquals(level.board, engine.state.board)
                    assertEquals(TrayState(), engine.state.taskTray)
                    assertFalse(engine.state.taskTray.isFull)
                    assertEquals(GameStatus.RUNNING, engine.state.status)
                    assertFalse(engine.isTileSelectable(lower.id))
                    assertEquals(expectedBeforeRestart, beforeRestart)
                }

                // Identical subsequent actions verify restored blocker counts through
                // public behavior, including the first removal leaving lower blocked.
                for (id in order) {
                    fresh.selectTile(id)
                    engine.selectTile(id)
                    assertEquivalentGameplay(fresh, engine, ids)
                    assertEquals(expectedBeforeRestart, beforeRestart)
                }
                assertEquals(GameStatus.WON, engine.state.status)
                engine.restart()
                assertEquivalentGameplay(DefaultGameEngine(level), engine, ids)
            }
        }
    }

    private fun assertEquivalentGameplay(expected: GameEngine, actual: GameEngine, ids: List<String>) {
        assertEquals(expected.state, actual.state)
        for (id in ids) {
            assertEquals("Availability for $id", expected.isTileSelectable(id), actual.isTileSelectable(id))
        }
    }

    private fun assertTerminalFrozen(engine: GameEngine, ids: List<String>) {
        val terminal = engine.state
        val counts = blockerCounts(engine)
        for (id in ids) {
            assertFalse(engine.isTileSelectable(id))
            engine.selectTile(id)
            engine.undo()
            engine.shuffle()
            assertSame(terminal, engine.state)
            assertEquals(counts, blockerCounts(engine))
            assertFalse(engine.isTileSelectable(id))
        }
    }

    private fun blockerCounts(engine: GameEngine): Map<*, *> {
        val graphField = DefaultGameEngine::class.java.getDeclaredField("overlapGraph")
        graphField.isAccessible = true
        val countsField = OverlapGraph::class.java.getDeclaredField("activeBlockerCounts")
        countsField.isAccessible = true
        return (countsField.get(graphField.get(engine)) as Map<*, *>).toMap()
    }

    // DEFAULT is the only type. Custom capacities use the existing test-only
    // reflection convention without introducing a production mutation API.
    private fun setTestState(engine: GameEngine, state: GameState) {
        val stateField = DefaultGameEngine::class.java.getDeclaredField("currentState")
        stateField.isAccessible = true
        stateField.set(engine, state)
    }

    private fun tile(id: String, layer: Int, column: Int = 0) =
        Tile(id, TileType.DEFAULT, TilePosition(0, column, layer))

    private fun engineWith(vararg tiles: Tile): GameEngine = DefaultGameEngine(
        FixedLevels.SAMPLE_LEVEL.copy(board = Board(tiles.toList()))
    )
}
