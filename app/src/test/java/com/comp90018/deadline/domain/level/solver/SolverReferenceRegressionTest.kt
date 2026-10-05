package com.comp90018.deadline.domain.level.solver

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.LayoutTemplate
import com.comp90018.deadline.domain.level.model.LevelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SolverReferenceRegressionTest {
    /** Small exhaustive oracle that does not use OverlapGraph, solver, or engine helpers. */
    private fun referenceCanWin(tiles: List<Tile>): Boolean {
        val failed = mutableSetOf<Int>()
        fun search(remaining: Int, counts: List<Int>): Boolean {
            if (remaining == 0) return counts.sum() == 0
            if (counts.sum() >= 7 || remaining in failed) return false
            for (index in tiles.indices) {
                if (remaining and (1 shl index) == 0) continue
                val tile = tiles[index]
                val blocked = tiles.indices.any { other ->
                    remaining and (1 shl other) != 0 &&
                        tiles[other].position.layer > tile.position.layer &&
                        abs(tiles[other].position.row.toLong() - tile.position.row) < 2 &&
                        abs(tiles[other].position.column.toLong() - tile.position.column) < 2
                }
                if (blocked) continue
                val next = counts.toMutableList()
                next[tile.type.ordinal] = (next[tile.type.ordinal] + 1) % 3
                if (search(remaining xor (1 shl index), next)) return true
            }
            // For a fixed board, removed tile counts determine tray counts modulo three.
            failed.add(remaining)
            return false
        }
        return search((1 shl tiles.size) - 1, List(TileType.entries.size) { 0 })
    }

    @Test
    fun solverAgreesWithIndependentGeometryAndTraySearchOnTwoHundredStaggeredBoards() {
        val validator = SolvabilityValidator(BacktrackingSolver(maxVisitedStates = 20000))
        var solvableCount = 0
        var unsolvableCount = 0
        repeat(200) { seed ->
            val types = List(3) { TileType.entries.toList() }.flatten().shuffled(Random(seed * 104729))
            val tiles = types.mapIndexed { index, type ->
                val layer = index / 2
                Tile("t$index", type, TilePosition(layer % 2, (index % 2) * 4 + layer % 2, layer))
            }
            val level = Level("seed_$seed", "Reference", Board(tiles), LevelConfig(LayoutTemplate(2, 3), 12, 5))
            val expected = referenceCanWin(tiles)
            val result = validator.validate(level)
            assertTrue("seed=$seed result=$result", result is SolvabilityResult.Solvable || result == SolvabilityResult.Unsolvable)
            assertEquals("seed=$seed", expected, result is SolvabilityResult.Solvable)
            if (result is SolvabilityResult.Solvable) {
                solvableCount++
                val engine = DefaultGameEngine(level, maxUndoDepth = 0)
                assertEquals(tiles.size, result.moves.size)
                assertEquals(tiles.size, result.moves.toSet().size)
                result.moves.forEach { id ->
                    assertTrue("seed=$seed move=$id", engine.isTileSelectable(id))
                    engine.selectTile(id)
                }
                assertEquals("seed=$seed", GameStatus.WON, engine.state.status)
                assertTrue(engine.state.taskTray.tiles.isEmpty())
            } else {
                unsolvableCount++
            }
        }
        // Guard against accidentally weakening the fixtures into only positive examples.
        assertTrue("No solvable fixtures", solvableCount > 0)
        assertTrue("No unsolvable fixtures", unsolvableCount > 0)
    }
}
