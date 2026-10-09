package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Time-based Stress accumulation and Maximum Stress input degradation (issue #58). */
class TimeBasedStressTest {
    @Test
    fun stressGrowsOnePointFivePercentPerSecond() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1)

        engine.advanceTime(1_000L)
        assertEquals("1.5 points round down until the half point is banked", 1, engine.state.stress)
        engine.advanceTime(1_000L)
        assertEquals(3, engine.state.stress)
        engine.advanceTime(8_000L)
        assertEquals(15, engine.state.stress)
    }

    @Test
    fun shortTicksLoseNoAccumulation() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1)

        repeat(100) { engine.advanceTime(100L) }

        assertEquals(15, engine.state.stress)
    }

    @Test
    fun tileSelectionAloneNeverAddsStress() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1, initialStress = 10)

        engine.selectTile("level_1_book_1")
        engine.selectTile("level_1_coffee_1")

        assertEquals(10, engine.state.stress)
    }

    @Test
    fun stressReachesButNeverExceedsTheMaximum() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1)

        engine.advanceTime(66_000L)
        assertEquals(99, engine.state.stress)
        engine.advanceTime(1_000L)
        assertEquals(100, engine.state.stress)
        engine.advanceTime(Long.MAX_VALUE / 2)
        assertEquals(100, engine.state.stress)
    }

    @Test
    fun timeSpentAtMaximumIsNotBankedForAfterRecovery() {
        val engine =
            DefaultGameEngine(
                FixedLevels.LEVEL_1,
                initialStress = 100,
                stressConfig = StressConfig(degradationProbability = 0.0),
            )

        engine.advanceTime(30_000L)
        COFFEE_IDS.forEach(engine::selectTile)
        assertEquals(80, engine.state.stress)

        engine.advanceTime(0L)
        assertEquals(80, engine.state.stress)
    }

    @Test
    fun stressStopsOnceTheGameHasEnded() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1)
        (COFFEE_IDS + BOOK_IDS).forEach(engine::selectTile)
        assertEquals(GameStatus.WON, engine.state.status)

        engine.advanceTime(60_000L)

        assertEquals(0, engine.state.stress)
    }

    @Test
    fun restartResetsStressAndTheBankedFraction() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1)
        engine.advanceTime(10_500L)
        assertEquals(15, engine.state.stress)

        engine.restart()
        assertEquals(0, engine.state.stress)
        engine.advanceTime(500L)

        assertEquals("A fraction banked before restart must not carry over", 0, engine.state.stress)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeElapsedTime() {
        DefaultGameEngine(FixedLevels.LEVEL_1).advanceTime(-1L)
    }

    @Test
    fun belowMaximumStressSelectionsAreNeverRedirected() {
        val engine = rowEngine(initialStress = 99, probability = 1.0)

        engine.selectTile("t1")

        assertEquals(listOf("t1"), engine.state.taskTray.tiles.map { it.id })
    }

    @Test
    fun atMaximumStressACertainSlipSelectsANeighbourInstead() {
        val engine = rowEngine(initialStress = 100, probability = 1.0)

        engine.selectTile("t1")

        val selected = engine.state.taskTray.tiles.single().id
        assertNotEquals("t1", selected)
        assertTrue("Slip must land on a neighbour of t1", selected in setOf("t0", "t2"))
        assertTrue("The requested tile stays on the board", engine.state.board.tiles.any { it.id == "t1" })
    }

    @Test
    fun atMaximumStressAZeroProbabilityNeverSlips() {
        val engine = rowEngine(initialStress = 100, probability = 0.0)

        engine.selectTile("t1")

        assertEquals(listOf("t1"), engine.state.taskTray.tiles.map { it.id })
    }

    @Test
    fun slipsNeverLandOnCoveredTilesAndFallBackWhenNoNeighbourIsSelectable() {
        // "b" is the only neighbour of "a" and it is covered by "top", which is too far from
        // "a" to count as a neighbour itself.
        val tiles =
            listOf(
                Tile("a", TileType.BOOK, TilePosition(0, 0, 0)),
                Tile("b", TileType.BOOK, TilePosition(0, 2, 0)),
                Tile("top", TileType.BOOK, TilePosition(0, 3, 1)),
            )
        repeat(20) { seed ->
            val engine = engine(tiles, initialStress = 100, probability = 1.0, seed = seed)

            engine.selectTile("a")

            assertEquals(listOf("a"), engine.state.taskTray.tiles.map { it.id })
        }
    }

    @Test
    fun aboutTwentyPercentOfMaximumStressSelectionsSlip() {
        val slips =
            (0 until 1_000).count { seed ->
                val engine = rowEngine(initialStress = 100, probability = StressConfig.DEFAULT_DEGRADATION_PROBABILITY, seed = seed)
                engine.selectTile("t1")
                engine.state.taskTray.tiles.single().id != "t1"
            }

        assertTrue("Expected roughly 200 of 1000 selections to slip, got $slips", slips in 150..250)
    }

    @Test
    fun coffeeRecoveryLeavesMaximumStressAndStopsSlips() {
        val engine =
            DefaultGameEngine(
                FixedLevels.LEVEL_1,
                initialStress = 100,
                stressConfig = StressConfig(degradationProbability = 0.0),
                random = Random(1),
            )
        val manager = StressManager(engine.stressConfig)
        assertTrue(manager.isMaxStress(engine.state.stress))

        COFFEE_IDS.forEach(engine::selectTile)

        assertFalse(manager.isMaxStress(engine.state.stress))
    }

    private fun rowEngine(
        initialStress: Int,
        probability: Double,
        seed: Int = 7,
    ) = engine(
        tiles = List(6) { Tile("t$it", TileType.entries[it % 3], TilePosition(0, it * 2, 0)) },
        initialStress = initialStress,
        probability = probability,
        seed = seed,
    )

    private fun engine(
        tiles: List<Tile>,
        initialStress: Int,
        probability: Double,
        seed: Int,
    ) = DefaultGameEngine(
        FixedLevels.LEVEL_1.copy(board = Board(tiles)),
        initialStress = initialStress,
        stressConfig = StressConfig(degradationProbability = probability),
        random = Random(seed),
    )

    private companion object {
        val COFFEE_IDS = listOf("level_1_coffee_1", "level_1_coffee_2", "level_1_coffee_3")
        val BOOK_IDS = listOf("level_1_book_1", "level_1_book_2", "level_1_book_3")
    }
}
