package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.TilePosition

/**
 * Precomputed edges point from each upper tile to every lower tile it covers.
 * Coverage follows [TilePosition]: positive-area intersection of 2 × 2 footprints
 * and a strictly higher layer. Edges include non-adjacent layers, not just immediate layers.
 * Runtime counts contain only active tiles; zero blockers means selectable.
 */
internal class OverlapGraph(board: Board) {
    private val coveredNeighbours: Map<String, List<String>>
    private val initialBlockerCounts: Map<String, Int>
    private val activeBlockerCounts: MutableMap<String, Int>

    init {
        val tiles = board.tiles
        require(tiles.map { it.id }.toSet().size == tiles.size) { "Board tile IDs must be unique." }
        val neighbours = tiles.associate { it.id to mutableListOf<String>() }
        val counts = tiles.associate { it.id to 0 }.toMutableMap()
        for (upper in tiles) {
            for (lower in tiles) {
                if (covers(upper.position, lower.position)) {
                    neighbours.getValue(upper.id).add(lower.id)
                    counts[lower.id] = counts.getValue(lower.id) + 1
                }
            }
        }
        coveredNeighbours = neighbours.mapValues { (_, ids) -> ids.toList() }
        initialBlockerCounts = counts.toMap()
        activeBlockerCounts = counts
    }

    fun isSelectable(tileId: String): Boolean = activeBlockerCounts[tileId] == 0

    /** Rejected actions are no-ops. A removal updates only this tile's lower neighbours. */
    fun remove(tileId: String): Boolean {
        if (!isSelectable(tileId)) return false
        activeBlockerCounts.remove(tileId)
        for (lowerId in coveredNeighbours.getValue(tileId)) {
            val count = activeBlockerCounts[lowerId] ?: continue
            activeBlockerCounts[lowerId] = count - 1
        }
        return true
    }

    /** Restores all active tiles and counts without recalculating geometric intersections. */
    fun reset() {
        activeBlockerCounts.clear()
        activeBlockerCounts.putAll(initialBlockerCounts)
    }

    private fun covers(upper: TilePosition, lower: TilePosition): Boolean =
        upper.layer > lower.layer &&
            kotlin.math.abs(upper.row.toLong() - lower.row.toLong()) < 2L &&
            kotlin.math.abs(upper.column.toLong() - lower.column.toLong()) < 2L
}
