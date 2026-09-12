package com.comp90018.deadline.domain.game.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModelsTest {
    @Test
    fun constructsLayeredBoardAndTraySession() {
        val lower = Tile("lower", TileType.DEFAULT, TilePosition(2, 3))
        val upper = Tile("upper", TileType.DEFAULT, TilePosition(2, 3, 1))
        val collected = Tile("collected", TileType.DEFAULT, TilePosition(0, 1))
        val state = GameState(Board(listOf(lower, upper)), TrayState(listOf(collected)))

        assertEquals(0, lower.position.layer)
        assertEquals(TilePosition(2, 3, 1), state.board.tiles[1].position)
        assertEquals(listOf(collected), state.taskTray.tiles)
        assertEquals(GameStatus.RUNNING, state.status)
    }

    @Test
    fun defaultTrayHasSevenSlotsAndIsNotFull() {
        val tray = TrayState()

        assertEquals(7, tray.capacity)
        assertTrue(tray.tiles.isEmpty())
        assertFalse(tray.isFull)
    }

    @Test
    fun fullnessUsesConfiguredCapacity() {
        val tiles = (1..7).map { Tile("tile-$it", TileType.DEFAULT, TilePosition(0, it)) }

        assertFalse(TrayState(tiles.take(6)).isFull)
        assertTrue(TrayState(tiles).isFull)
        assertFalse(TrayState(tiles.take(1), capacity = 2).isFull)
        assertTrue(TrayState(tiles.take(2), capacity = 2).isFull)
    }

    @Test
    fun statusIsExplicitAndSupportsBothOutcomes() {
        assertEquals(GameStatus.RUNNING, GameState().status)
        assertEquals(GameStatus.WON, GameState(status = GameStatus.WON).status)
        val tile = Tile("last", TileType.DEFAULT, TilePosition(0, 0))
        val fullTray = TrayState(listOf(tile), capacity = 1)
        assertEquals(GameStatus.RUNNING, GameState(taskTray = fullTray).status)
        assertEquals(GameStatus.LOST, GameState(taskTray = fullTray, status = GameStatus.LOST).status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroCapacity() {
        TrayState(capacity = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeCapacity() {
        TrayState(capacity = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOverCapacityTray() {
        val tiles = (1..2).map { Tile("tile-$it", TileType.DEFAULT, TilePosition(0, it)) }
        TrayState(tiles, capacity = 1)
    }

    @Test
    fun acceptsZeroBasedOrigin() {
        val position = TilePosition(0, 0, 0)

        assertEquals(0, position.row)
        assertEquals(0, position.column)
        assertEquals(0, position.layer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeRow() {
        TilePosition(-1, 0, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeColumn() {
        TilePosition(0, -1, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLayer() {
        TilePosition(0, 0, -1)
    }
}
