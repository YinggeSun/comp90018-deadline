package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InputDegradationTest {
    @Test
    fun selectionIsUntouchedBelowTheHighStressThreshold() {
        val degradation = degradation(probability = 1.0)

        assertEquals(
            "requested",
            degradation.resolveSelection("requested", stress = 74, eligibleNeighbours = listOf("other")),
        )
    }

    @Test
    fun selectionIsUntouchedWhenNoCandidateRemains() {
        val degradation = degradation(probability = 1.0)

        assertEquals("requested", degradation.resolveSelection("requested", 100, emptyList()))
        assertEquals("requested", degradation.resolveSelection("requested", 100, listOf("requested")))
    }

    @Test
    fun zeroProbabilityDisablesDegradation() {
        val degradation = degradation(probability = 0.0)

        repeat(50) {
            assertEquals("requested", degradation.resolveSelection("requested", 100, listOf("a", "b")))
        }
    }

    @Test
    fun certainProbabilityAlwaysRedirectsToACandidate() {
        val degradation = degradation(probability = 1.0)

        repeat(50) {
            val selected = degradation.resolveSelection("requested", 100, listOf("a", "b"))
            assertNotEquals("requested", selected)
            assertTrue(selected in listOf("a", "b"))
        }
    }

    @Test
    fun redirectsReachEveryCandidate() {
        val degradation = degradation(probability = 1.0)
        val candidates = listOf("a", "b", "c")

        val selected =
            (1..300).map {
                degradation.resolveSelection("requested", 100, candidates)
            }.toSet()

        assertEquals(candidates.toSet(), selected)
    }

    @Test
    fun probabilityGovernsHowOftenSelectionSlips() {
        val degradation = degradation(probability = 0.25)

        val redirects =
            (1..1000).count {
                degradation.resolveSelection("requested", 100, listOf("a", "b")) != "requested"
            }

        assertTrue("Expected roughly a quarter of 1000 selections to slip, got $redirects", redirects in 200..300)
    }

    @Test
    fun neighboursExcludeCoveredTilesThatAreNotSelectable() {
        val tiles =
            listOf(
                tile("requested", 2, 2),
                tile("covered", 2, 4),
                tile("free", 4, 4),
            )

        val neighbours = degradation().eligibleNeighbours("requested", tiles, setOf("requested", "free"))

        assertEquals(listOf("free"), neighbours)
    }

    @Test
    fun neighboursIncludeTouchingAndOverlappingFootprintsOnAnyLayer() {
        val tiles =
            listOf(
                tile("requested", 2, 2),
                tile("edge", 2, 4),
                tile("corner", 0, 0),
                tile("stacked", 2, 2, 1),
                tile("farColumn", 2, 5),
                tile("farRow", 5, 2),
            )

        val neighbours = degradation().eligibleNeighbours("requested", tiles, tiles.map { it.id }.toSet())

        assertEquals(listOf("edge", "corner", "stacked"), neighbours)
    }

    @Test
    fun neighboursNeverIncludeTheRequestedTile() {
        val tiles = listOf(tile("requested", 2, 2), tile("other", 2, 3))

        val neighbours = degradation().eligibleNeighbours("requested", tiles, setOf("requested", "other"))

        assertEquals(listOf("other"), neighbours)
    }

    @Test
    fun unknownRequestedTileHasNoNeighbours() {
        val tiles = listOf(tile("a", 0, 0), tile("b", 1, 1))

        assertTrue(degradation().eligibleNeighbours("missing", tiles, setOf("a", "b")).isEmpty())
    }

    @Test
    fun redirectOnlyEverLandsOnASuppliedSelectableTile() {
        val tiles =
            listOf(
                tile("requested", 2, 2),
                tile("covered", 2, 3),
                tile("selectable", 2, 4),
            )
        val degradation = degradation(probability = 1.0)
        val selectableIds = setOf("requested", "selectable")

        repeat(50) {
            val neighbours = degradation.eligibleNeighbours("requested", tiles, selectableIds)
            assertEquals("selectable", degradation.resolveSelection("requested", 100, neighbours))
        }
    }

    private fun degradation(
        probability: Double = 0.25,
        seed: Int = 20250929,
    ) = InputDegradation(
        config = StressConfig(degradationProbability = probability),
        random = Random(seed),
    )

    private fun tile(
        id: String,
        row: Int,
        column: Int,
        layer: Int = 0,
    ) = Tile(id, TileType.DEFAULT, TilePosition(row, column, layer))
}
