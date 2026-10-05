package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineCapacityRegressionTest {
    private fun engine(types: List<TileType>): DefaultGameEngine =
        DefaultGameEngine(
            FixedLevels.LEVEL_1.copy(
                board = Board(types.mapIndexed { index, type -> Tile("t$index", type, TilePosition(0, index * 2)) }),
            ),
            initialStress = 30,
        )

    @Test
    fun seventhNonMatchingSelectionLosesThroughPublicApiAndRestartRestoresSession() {
        val engine = engine(TileType.entries.flatMap { type -> List(2) { type } })
        val initial = engine.state
        repeat(6) { engine.selectTile("t$it") }
        assertTrue(engine.canUndo)
        engine.selectTile("t6")
        assertEquals(GameStatus.LOST, engine.state.status)
        assertEquals(7, engine.state.taskTray.tiles.size)
        assertEquals(44, engine.state.stress)
        assertFalse(engine.canUndo)
        val lost = engine.state
        engine.undo()
        engine.shuffle()
        engine.selectTile("t7")
        assertEquals(lost, engine.state)
        engine.restart()
        assertEquals(initial, engine.state)
        assertFalse(engine.canUndo)
        assertTrue(engine.isTileSelectable("t7"))
    }

    @Test
    fun seventhSlotCoffeeMatchClearsBeforeLossAndCommitsUndoHistory() {
        val types =
            listOf(
                TileType.COFFEE,
                TileType.BOOK,
                TileType.LAPTOP,
                TileType.DEFAULT,
                TileType.COFFEE,
                TileType.BOOK,
                TileType.COFFEE,
                TileType.LAPTOP,
            )
        val engine = engine(types)
        repeat(6) { engine.selectTile("t$it") }
        assertTrue(engine.canUndo)
        engine.selectTile("t6")
        assertEquals(GameStatus.RUNNING, engine.state.status)
        assertEquals(listOf("t1", "t2", "t3", "t5"), engine.state.taskTray.tiles.map { it.id })
        assertEquals(24, engine.state.stress)
        assertFalse(engine.canUndo)
        val committed = engine.state
        engine.undo()
        assertEquals(committed, engine.state)
        engine.selectTile("t7")
        assertEquals(GameStatus.WON, engine.state.status)
        assertFalse(engine.canUndo)
    }
}
