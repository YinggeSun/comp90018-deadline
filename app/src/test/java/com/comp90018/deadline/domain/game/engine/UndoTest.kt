package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.*
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.*
import org.junit.Test

class UndoTest {
    @Test
    fun postMatchHistoryHonorsZeroSingleAndBoundedDepths() {
        for (depth in listOf(0, 1, 2)) {
            val tiles = (0..5).map { tile("t$it", 0, it * 4) }
            val engine = engine(depth, tiles)
            for (id in listOf("t0", "t1", "t2")) engine.selectTile(id)
            val checkpoint = capture(engine)
            assertEquals(0, historySize(engine))
            repeat(3) { undoTo(engine, checkpoint) }
            engine.selectTile("t3")
            val afterFirst = capture(engine)
            engine.selectTile("t4")
            val afterSecond = capture(engine)
            assertEquals(depth, historySize(engine))
            when (depth) {
                0 -> undoTo(engine, afterSecond)
                1 -> {
                    undoTo(engine, afterFirst)
                    undoTo(engine, afterFirst)
                }
                else -> {
                    undoTo(engine, afterFirst)
                    undoTo(engine, checkpoint)
                    undoTo(engine, checkpoint)
                }
            }
            assertEquals(0, historySize(engine))
            for (id in listOf("t0", "t1", "t2")) {
                assertFalse(counts(engine).containsKey(id))
                assertFalse((engine.state.board.tiles + engine.state.taskTray.tiles).any { it.id == id })
            }
        }
    }

    @Test
    fun matchBoundaryKeepsMatchedBlockersRemovedWhileNewMovesRewind() {
        val engine = engine(tiles = listOf(
            tile("a", 0, 8), tile("b", 0, 12), tile("upper", 3),
            tile("middle", 1), tile("lower", 0), tile("other", 0, 16)
        ))
        for (id in listOf("a", "b", "upper")) engine.selectTile(id)
        val checkpoint = capture(engine)
        assertTrue(engine.isTileSelectable("middle"))
        assertFalse(engine.isTileSelectable("lower"))
        repeat(3) { undoTo(engine, checkpoint) }
        engine.selectTile("middle")
        assertTrue(engine.isTileSelectable("lower"))
        undoTo(engine, checkpoint)
        assertFalse(engine.isTileSelectable("lower"))
        assertFalse(engine.isTileSelectable("upper"))
        assertFalse(counts(engine).containsKey("upper"))
        undoTo(engine, checkpoint)
    }

    @Test
    fun winningMatchClearsAllHistory() {
        val engine = engine(tiles = listOf("a", "b", "c").mapIndexed { i, id -> tile(id, 0, i * 4) })
        engine.selectTile("a")
        engine.selectTile("b")
        assertEquals(2, historySize(engine))
        engine.selectTile("c")
        assertEquals(GameStatus.WON, engine.state.status)
        assertTrue(engine.state.board.tiles.isEmpty())
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertEquals(0, historySize(engine))
        val won = capture(engine)
        repeat(3) { undoTo(engine, won) }
        assertEquals(0, historySize(engine))
    }

    @Test
    fun newSelectionAfterUndoRetainsOnlyCurrentBranch() {
        val engine = engine()
        val initial = capture(engine)
        engine.selectTile("a")
        val afterA = capture(engine)
        engine.selectTile("b")
        undoTo(engine, afterA)
        engine.selectTile("c")
        undoTo(engine, afterA)
        undoTo(engine, initial)
        undoTo(engine, initial)
    }

    @Test
    fun independentCoveringBlockersReturnOneAtATime() {
        val engine = engine(tiles = listOf(tile("lower", 0), tile("a", 1), tile("b", 1)))
        val initial = capture(engine)
        engine.selectTile("a")
        val afterA = capture(engine)
        engine.selectTile("b")
        assertTrue(engine.isTileSelectable("lower"))
        undoTo(engine, afterA)
        assertFalse(engine.isTileSelectable("lower"))
        undoTo(engine, initial)
        assertEquals(2, counts(engine)["lower"])
    }

    @Test
    fun basicUndoRestoresMiddleBoardTileAndExactInstances() {
        val engine = engine()
        val before = capture(engine)
        engine.selectTile("b")
        undoTo(engine, before)
        assertEquals(listOf("a", "b", "c", "d"), engine.state.board.tiles.map { it.id })
        engine.undo()
        assertSame(before.state, engine.state)
    }

    @Test
    fun matchClearsHistoryAndPostMatchUndosPreserveAllSnapshots() {
        val engine = engine(tiles = listOf("a", "b", "c", "d", "e", "f").mapIndexed { i, id -> tile(id, 0, i * 4) })
        setState(engine, engine.state.copy(taskTray = TrayState(capacity = 3)))
        val snapshots = mutableListOf(capture(engine))
        for (id in listOf("b", "a", "c")) {
            engine.selectTile(id)
            snapshots.add(capture(engine))
        }
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        val copies = snapshots.map { it.state.copy(
            board = Board(it.state.board.tiles.toList()),
            taskTray = it.state.taskTray.copy(tiles = it.state.taskTray.tiles.toList())
        ) }
        val checkpoint = capture(engine)
        assertEquals(0, historySize(engine))
        repeat(3) { undoTo(engine, checkpoint) }
        engine.selectTile("d")
        val afterD = capture(engine)
        engine.selectTile("e")
        val afterE = capture(engine)
        undoTo(engine, afterD)
        undoTo(engine, checkpoint)
        undoTo(engine, checkpoint)
        for (id in listOf("a", "b", "c")) {
            assertFalse(engine.isTileSelectable(id))
            assertFalse(counts(engine).containsKey(id))
        }
        assertEquals(listOf("d"), afterD.state.taskTray.tiles.map { it.id })
        assertEquals(listOf("d", "e"), afterE.state.taskTray.tiles.map { it.id })
        engine.restart()
        snapshots.zip(copies).forEach { (snapshot, copy) -> assertEquals(copy, snapshot.state) }
    }

    @Test
    fun multipleLayerBlockerCountsRewindExactlyAndCanBeSelectedAgain() {
        val tiles = listOf(tile("bottom", 0), tile("middle", 3), tile("top", 7), tile("other", 0, 8))
        val engine = engine(tiles = tiles)
        val initial = capture(engine)
        engine.selectTile("top")
        val afterTop = capture(engine)
        assertFalse(engine.isTileSelectable("bottom"))
        engine.selectTile("middle")
        assertTrue(engine.isTileSelectable("bottom"))
        undoTo(engine, afterTop)
        assertFalse(engine.isTileSelectable("bottom"))
        undoTo(engine, initial)
        engine.selectTile("top")
        assertEquals(afterTop.counts, counts(engine))
        assertEquals(afterTop.state, engine.state)
    }

    @Test
    fun rejectedIdsDoNotConsumeBoundedHistory() {
        val engine = engine(depth = 1, tiles = listOf(tile("lower", 0), tile("upper", 1), tile("other", 0, 8)))
        val initial = capture(engine)
        engine.selectTile("lower")
        engine.selectTile("unknown")
        engine.undo()
        assertSame(initial.state, engine.state)
        engine.selectTile("other")
        val selected = engine.state
        for (id in listOf("lower", "unknown", "other")) {
            engine.selectTile(id)
            assertSame(selected, engine.state)
        }
        undoTo(engine, initial)
        engine.undo()
        assertSame(initial.state, engine.state)
    }

    @Test
    fun fullRunningTrayDoesNotRecordOrReplaceHistory() {
        for (hasHistory in listOf(false, true)) {
            val engine = engine(depth = 1)
            val initial = capture(engine)
            if (hasHistory) engine.selectTile("a")
            val fillers = (0..6).map { tile("f$it", 0, it * 4) }
            setState(engine, engine.state.copy(taskTray = TrayState(fillers)))
            val full = capture(engine)
            engine.selectTile("b")
            assertSame(full.state, engine.state)
            assertEquals(full.counts, counts(engine))
            undoTo(engine, if (hasHistory) initial else full)
        }
    }

    @Test
    fun emptyHistoryIsStrictNoOp() {
        val engine = engine()
        undoTo(engine, capture(engine))
    }

    @Test
    fun depthOneRetainsOnlyMostRecentMove() = assertDepth(1)

    @Test
    fun depthTwoDiscardsOldestMove() = assertDepth(2)

    @Test
    fun depthZeroNeverStoresHistoryAcrossRestart() {
        val engine = engine(depth = 0)
        repeat(2) {
            engine.selectTile("a")
            assertEquals(listOf("a"), engine.state.taskTray.tiles.map { it.id })
            undoTo(engine, capture(engine))
            assertEquals(0, historySize(engine))
            engine.restart()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDepthIsRejected() {
        engine(depth = -1)
    }

    @Test
    fun restartClearsHistoryAndNewMovesStartFreshHistory() {
        val engine = engine()
        val initial = capture(engine)
        engine.selectTile("a")
        engine.selectTile("b")
        engine.restart()
        undoTo(engine, initial)
        assertEquals(0, historySize(engine))
        engine.selectTile("c")
        undoTo(engine, initial)
        engine.undo()
        assertSame(initial.state, engine.state)
    }

    @Test
    fun winningUndoDoesNotMutateGraphOrConsumeHistory() = assertTerminal(false)

    @Test
    fun losingUndoDoesNotMutateGraphOrConsumeHistory() = assertTerminal(true)

    private fun assertTerminal(loss: Boolean) {
        val engine = engine()
        if (loss) setState(engine, engine.state.copy(taskTray = TrayState(capacity = 1)))
        for (id in if (loss) listOf("a") else listOf("a", "b", "c", "d")) engine.selectTile(id)
        assertEquals(if (loss) GameStatus.LOST else GameStatus.WON, engine.state.status)
        val terminal = capture(engine)
        val size = historySize(engine)
        repeat(2) {
            engine.selectTile("b")
            undoTo(engine, terminal)
            assertEquals(size, historySize(engine))
        }
        engine.restart()
        undoTo(engine, capture(engine))
        assertEquals(0, historySize(engine))
    }

    private fun assertDepth(depth: Int) {
        val engine = engine(depth)
        // DEFAULT alone matches every third selection. Seed three tray tiles to exercise
        // three non-matching transitions and oldest-entry trimming without fake tile types.
        setState(engine, engine.state.copy(taskTray = TrayState((1..3).map { tile("seed-$it", 0, it * 4) })))
        val before = mutableListOf<Snapshot>()
        for (id in listOf("a", "b", "c")) {
            before.add(capture(engine))
            engine.selectTile(id)
        }
        assertEquals(depth, historySize(engine))
        before.takeLast(depth).asReversed().forEach { undoTo(engine, it) }
        undoTo(engine, capture(engine))
        assertEquals(0, historySize(engine))
    }

    private data class Snapshot(val state: GameState, val counts: Map<*, *>, val availability: Map<String, Boolean>)

    private fun capture(engine: DefaultGameEngine): Snapshot = Snapshot(
        engine.state, counts(engine),
        (engine.state.board.tiles + engine.state.taskTray.tiles).associate { it.id to engine.isTileSelectable(it.id) }
    )

    private fun undoTo(engine: DefaultGameEngine, expected: Snapshot) {
        engine.undo()
        assertSame(expected.state, engine.state)
        assertEquals(expected.counts, counts(engine))
        expected.availability.forEach { (id, available) -> assertEquals(available, engine.isTileSelectable(id)) }
        val tiles = engine.state.board.tiles + engine.state.taskTray.tiles
        assertEquals(tiles.size, tiles.map { it.id }.toSet().size)
        assertEquals((expected.state.board.tiles + expected.state.taskTray.tiles).map { it.id }.toSet(), tiles.map { it.id }.toSet())
    }

    private fun counts(engine: DefaultGameEngine): Map<*, *> {
        val graph = field(engine, "overlapGraph")!!
        return (field(graph, "activeBlockerCounts") as Map<*, *>).toMap()
    }

    private fun historySize(engine: DefaultGameEngine) = (field(engine, "history") as Collection<*>).size

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply {
        isAccessible = true
    }.get(target)

    // Test-only fixtures follow the existing engine tests; production state stays read-only.
    private fun setState(engine: DefaultGameEngine, state: GameState) {
        engine.javaClass.getDeclaredField("currentState").apply { isAccessible = true }.set(engine, state)
    }

    private fun tile(id: String, layer: Int, column: Int = 0) = Tile(id, TileType.DEFAULT, TilePosition(0, column, layer))

    private fun engine(depth: Int = Int.MAX_VALUE, tiles: List<Tile> = listOf("a", "b", "c", "d").mapIndexed { i, id -> tile(id, 0, i * 4) }) =
        DefaultGameEngine(FixedLevels.SAMPLE_LEVEL.copy(board = Board(tiles)), maxUndoDepth = depth)
}
