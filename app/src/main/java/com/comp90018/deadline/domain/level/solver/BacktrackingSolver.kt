package com.comp90018.deadline.domain.level.solver

import com.comp90018.deadline.domain.game.engine.OverlapGraph
import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.game.model.TrayState
import java.math.BigInteger

/** Bounded depth-first search using the engine's actual overlap rules. */
class BacktrackingSolver(
    private val maxVisitedStates: Int = 100_000,
    private val maxSearchTiles: Int = 256
) {
    init {
        require(maxVisitedStates > 0) { "State budget must be positive." }
        require(maxSearchTiles in 1..256) { "Tile limit must be between 1 and 256." }
    }

    fun solve(board: Board): SolvabilityResult {
        val tiles = board.tiles
        if (tiles.isEmpty()) return SolvabilityResult.InvalidBoard("Board must not be empty.")
        if (tiles.map { it.id }.toSet().size != tiles.size) {
            return SolvabilityResult.InvalidBoard("Tile IDs must be unique.")
        }
        if (tiles.map { it.position }.toSet().size != tiles.size) {
            return SolvabilityResult.InvalidBoard("Tile positions must be unique.")
        }
        if (tiles.groupingBy { it.type }.eachCount().values.any { it % 3 != 0 }) {
            return SolvabilityResult.Unsolvable
        }
        if (tiles.size > maxSearchTiles) return SolvabilityResult.SearchLimitReached

        val graph = OverlapGraph(board)
        val tray = IntArray(TileType.entries.size)
        val path = mutableListOf<String>()
        val deadEnds = mutableSetOf<BigInteger>()
        var visited = 0

        fun search(remaining: BigInteger, traySize: Int): SolvabilityResult {
            if (remaining.signum() == 0) {
                return if (traySize == 0) SolvabilityResult.Solvable(path.toList())
                else SolvabilityResult.Unsolvable
            }
            if (traySize >= TrayState.DEFAULT_CAPACITY || remaining in deadEnds) {
                return SolvabilityResult.Unsolvable
            }
            if (visited >= maxVisitedStates) return SolvabilityResult.SearchLimitReached
            visited++

            // Prefer immediate matches, but explore every legal alternative if needed.
            val candidates = tiles.indices.filter {
                remaining.testBit(it) && graph.isSelectable(tiles[it].id)
            }.sortedByDescending { tray[tiles[it].type.ordinal] }

            for (index in candidates) {
                val tile = tiles[index]
                val type = tile.type.ordinal
                val oldCount = tray[type]
                val snapshot = graph.snapshot()
                check(graph.remove(tile.id))
                tray[type] = (oldCount + 1) % 3
                val nextSize = traySize + 1 - if (oldCount == 2) 3 else 0
                path.add(tile.id)
                val result = try {
                    search(remaining.clearBit(index), nextSize)
                } finally {
                    path.removeAt(path.lastIndex)
                    tray[type] = oldCount
                    graph.restore(snapshot)
                }
                if (result != SolvabilityResult.Unsolvable) return result
            }

            deadEnds.add(remaining)
            return SolvabilityResult.Unsolvable
        }

        return search(BigInteger.ONE.shiftLeft(tiles.size).subtract(BigInteger.ONE), 0)
    }
}
