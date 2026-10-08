package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.model.Tile
import kotlin.math.abs
import kotlin.random.Random

/**
 * High Stress makes the player's touch unreliable: a selection can slip onto a neighbouring
 * tile instead of the requested one.
 *
 * These rules never read the board's availability tracking themselves. The caller supplies
 * the set of currently selectable tile IDs, and [eligibleNeighbours] filters by it, which is
 * what guarantees a covered or already-removed tile is never chosen.
 */
class InputDegradation(
    private val config: StressConfig = StressConfig(),
    private val random: Random = Random.Default
) {
    private val stressManager = StressManager(config)

    /**
     * The tile that is actually selected. Returns [requestedTileId] unchanged below the High
     * Stress threshold, when no candidate remains, or when the probability roll does not
     * trigger; otherwise one of [eligibleNeighbours] chosen uniformly.
     *
     * The requested tile is dropped from the candidates so a triggered redirect always moves
     * the selection somewhere else.
     */
    fun resolveSelection(
        requestedTileId: String,
        stress: Int,
        eligibleNeighbours: List<String>
    ): String {
        if (!stressManager.isHighStress(stress)) return requestedTileId
        val candidates = eligibleNeighbours.filter { it != requestedTileId }
        if (candidates.isEmpty()) return requestedTileId
        if (random.nextDouble() >= config.degradationProbability) return requestedTileId
        return candidates[random.nextInt(candidates.size)]
    }

    /**
     * Selectable tiles whose footprint touches or overlaps the requested tile's footprint in
     * the plane, on any layer, excluding the requested tile itself. Board order is preserved.
     *
     * Footprints follow [com.comp90018.deadline.domain.game.model.TilePosition]: a tile spans
     * two logical units per axis, so anchors within two units on both axes are touching or
     * closer. An unknown [requestedTileId] has no neighbours.
     */
    fun eligibleNeighbours(
        requestedTileId: String,
        tiles: List<Tile>,
        selectableIds: Set<String>
    ): List<String> {
        val requested = tiles.find { it.id == requestedTileId } ?: return emptyList()
        return tiles.filter { candidate ->
            candidate.id != requestedTileId &&
                candidate.id in selectableIds &&
                isNeighbour(requested, candidate)
        }.map { it.id }
    }

    private fun isNeighbour(requested: Tile, candidate: Tile): Boolean =
        abs(requested.position.row.toLong() - candidate.position.row.toLong()) <= 2L &&
            abs(requested.position.column.toLong() - candidate.position.column.toLong()) <= 2L
}
