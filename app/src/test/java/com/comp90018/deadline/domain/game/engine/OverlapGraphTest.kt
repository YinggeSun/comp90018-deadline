package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapGraphTest {
    @Test
    fun horizontalPartialOverlap() = assertCovered(0, 1)

    @Test
    fun verticalPartialOverlap() = assertCovered(1, 0)

    @Test
    fun diagonalPartialOverlap() = assertCovered(1, 1)

    @Test
    fun identicalAnchorsOverlap() = assertCovered(0, 0)

    @Test
    fun edgesCornersAndSeparatedFootprintsDoNotBlock() {
        for ((row, column) in listOf(0 to 2, 2 to 0, 2 to 2, 4 to 4)) {
            val graph = graph(tile("upper", 0, 0, 1), tile("lower", row, column))
            assertTrue(graph.isSelectable("upper"))
            assertTrue(graph.isSelectable("lower"))
        }
    }

    @Test
    fun sameLayerFootprintsDoNotBlock() {
        val graph = graph(tile("a", 0, 0), tile("b", 1, 1))
        assertTrue(graph.isSelectable("a"))
        assertTrue(graph.isSelectable("b"))
    }

    @Test
    fun nonAdjacentLayersAndAllBlockersInStackAreCounted() {
        val graph = graph(tile("bottom", 0, 0), tile("middle", 0, 0, 3), tile("top", 0, 0, 7))
        assertFalse(graph.isSelectable("bottom"))
        assertFalse(graph.isSelectable("middle"))
        assertTrue(graph.remove("top"))
        assertTrue(graph.isSelectable("middle"))
        assertFalse(graph.isSelectable("bottom"))
        assertTrue(graph.remove("middle"))
        assertTrue(graph.isSelectable("bottom"))
    }

    @Test
    fun multipleBlockersRequireFinalRemovalAndRepeatedRemovalIsHarmless() {
        val graph = graph(tile("lower", 1, 1), tile("a", 0, 0, 1), tile("b", 2, 2, 1))
        assertFalse(graph.remove("lower"))
        assertTrue(graph.remove("a"))
        assertFalse(graph.remove("a"))
        assertFalse(graph.isSelectable("a"))
        assertFalse(graph.isSelectable("lower"))
        assertTrue(graph.remove("b"))
        assertTrue(graph.isSelectable("lower"))
    }

    @Test
    fun removalUpdatesEveryCoveredNeighbourAndLeavesUnrelatedBlockersIntact() {
        val graph = graph(
            tile("upper", 1, 1, 1), tile("left", 0, 0), tile("right", 2, 2),
            tile("otherUpper", 8, 8, 1), tile("otherLower", 8, 8), tile("unrelated", 16, 16)
        )
        assertTrue(graph.remove("unrelated"))
        assertFalse(graph.isSelectable("left"))
        assertFalse(graph.isSelectable("right"))
        assertFalse(graph.isSelectable("otherLower"))
        assertTrue(graph.remove("upper"))
        assertTrue(graph.isSelectable("left"))
        assertTrue(graph.isSelectable("right"))
        assertFalse(graph.isSelectable("otherLower"))
        assertTrue(graph.isSelectable("otherUpper"))
    }

    @Test
    fun maximumCoordinatesDoNotOverflowIntersectionArithmetic() {
        val graph = graph(
            tile("upper", Int.MAX_VALUE, Int.MAX_VALUE, 1),
            tile("lower", Int.MAX_VALUE - 1, Int.MAX_VALUE - 1), tile("far", 0, 0)
        )
        assertFalse(graph.isSelectable("lower"))
        assertTrue(graph.isSelectable("far"))
        assertTrue(graph.remove("upper"))
        assertTrue(graph.isSelectable("lower"))
    }

    @Test
    fun emptyBoardAndUnknownIdsAreSafe() {
        val graph = graph()
        assertFalse(graph.isSelectable("unknown"))
        assertFalse(graph.remove("unknown"))
        graph.reset()
        assertFalse(graph.isSelectable("unknown"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsViolateBoardIdentityContract() {
        graph(tile("duplicate", 0, 0), tile("duplicate", 1, 1))
    }

    private fun assertCovered(row: Int, column: Int) {
        // Exercise both coordinate directions and input orders.
        for (reverse in listOf(false, true)) {
            val upper = tile("upper", if (reverse) row else 0, if (reverse) column else 0, 1)
            val lower = tile("lower", if (reverse) 0 else row, if (reverse) 0 else column)
            val graph = if (reverse) graph(lower, upper) else graph(upper, lower)
            assertTrue(graph.isSelectable("upper"))
            assertFalse(graph.isSelectable("lower"))
            assertFalse(graph.remove("lower"))
            assertTrue(graph.remove("upper"))
            assertTrue(graph.isSelectable("lower"))
        }
    }

    private fun tile(id: String, row: Int, column: Int, layer: Int = 0) =
        Tile(id, TileType.DEFAULT, TilePosition(row, column, layer))

    private fun graph(vararg tiles: Tile) = OverlapGraph(Board(tiles.toList()))
}
