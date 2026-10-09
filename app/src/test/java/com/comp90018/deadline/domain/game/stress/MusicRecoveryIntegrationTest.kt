package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Music effect as the engine applies it (issue #61 / #100). */
class MusicRecoveryIntegrationTest {
    @Test
    fun musicTripleClearsNormallyLowersStressAndStartsTheSlowdown() {
        val engine = engine(initialStress = 50)

        select(engine, "m1", "m2", "m3")

        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertTrue(engine.state.board.tiles.none { it.id in setOf("m1", "m2", "m3") })
        assertEquals(42, engine.state.stress)
        assertEquals(8_000L, engine.state.musicSlowdownRemainingMillis)
    }

    @Test
    fun stressGrowsAtHalfSpeedWhileTheEffectLasts() {
        val engine = engine(initialStress = 50)
        select(engine, "m1", "m2", "m3")

        engine.advanceTime(4_000L)

        // Half of the normal 6 points for four seconds.
        assertEquals(45, engine.state.stress)
        assertEquals(4_000L, engine.state.musicSlowdownRemainingMillis)
    }

    @Test
    fun normalGrowthResumesWhenTheEffectExpires() {
        val engine = engine(initialStress = 50)
        select(engine, "m1", "m2", "m3")

        // 8 s slowed (6 points) then 2 s at the normal rate (3 points).
        engine.advanceTime(10_000L)
        assertEquals(51, engine.state.stress)
        assertEquals(0L, engine.state.musicSlowdownRemainingMillis)

        engine.advanceTime(2_000L)
        assertEquals(54, engine.state.stress)
    }

    @Test
    fun aSecondMusicTripleRefreshesTheDurationWithoutStacking() {
        val engine = engine(initialStress = 60)
        select(engine, "m1", "m2", "m3")
        engine.advanceTime(4_000L)
        assertEquals(4_000L, engine.state.musicSlowdownRemainingMillis)

        select(engine, "m4", "m5", "m6")
        assertEquals(8_000L, engine.state.musicSlowdownRemainingMillis)
        val before = engine.state.stress

        engine.advanceTime(2_000L)

        // Still half speed, not a quarter: 1.5 points for two seconds.
        assertEquals(before + 1, engine.state.stress)
        engine.advanceTime(2_000L)
        assertEquals(before + 3, engine.state.stress)
    }

    @Test
    fun musicNeverPushesStressBelowZero() {
        val engine = engine(initialStress = 3)

        select(engine, "m1", "m2", "m3")

        assertEquals(0, engine.state.stress)
    }

    @Test
    fun musicRecoveryEndsMaximumStressAndTheSlowdownKeepsRunning() {
        val engine = engine(initialStress = 100, degradationProbability = 0.0)
        val manager = StressManager(engine.stressConfig)
        assertTrue(manager.isMaxStress(engine.state.stress))

        select(engine, "m1", "m2", "m3")

        assertEquals(92, engine.state.stress)
        assertFalse(manager.isMaxStress(engine.state.stress))
        engine.advanceTime(2_000L)
        assertEquals(6_000L, engine.state.musicSlowdownRemainingMillis)
    }

    @Test
    fun theSlowdownRunsDownEvenWhileStressIsAtItsMaximum() {
        val engine = engine(initialStress = 95, config = StressConfig(degradationProbability = 0.0, musicRecovery = 0))
        select(engine, "m1", "m2", "m3")
        engine.advanceTime(20_000L)
        assertEquals(100, engine.state.stress)

        assertEquals(0L, engine.state.musicSlowdownRemainingMillis)
    }

    @Test
    fun otherNewTypesAndCoffeeLeaveTheSlowdownAlone() {
        val engine = engine(initialStress = 50)

        select(engine, "e1", "e2", "e3")
        assertEquals("Exam is an ordinary match", 50, engine.state.stress)
        assertEquals(0L, engine.state.musicSlowdownRemainingMillis)

        select(engine, "c1", "c2", "c3")
        assertEquals("Coffee keeps its stronger recovery", 30, engine.state.stress)
        assertEquals(0L, engine.state.musicSlowdownRemainingMillis)
    }

    @Test
    fun coffeeAndMusicCloseTogetherApplyBothSafely() {
        val engine = engine(initialStress = 50)

        select(engine, "m1", "m2", "m3")
        select(engine, "c1", "c2", "c3")

        assertEquals(22, engine.state.stress)
        assertEquals("Coffee must not cancel the Music slowdown", 8_000L, engine.state.musicSlowdownRemainingMillis)
    }

    @Test
    fun undoKeepsTheSlowdownBecauseItFollowsPlayTime() {
        val engine = engine(initialStress = 50)
        select(engine, "m1", "m2", "m3")
        engine.selectTile("e1")
        engine.advanceTime(2_000L)

        engine.undo()

        assertEquals(6_000L, engine.state.musicSlowdownRemainingMillis)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
    }

    @Test
    fun restartClearsTheSlowdown() {
        val engine = engine(initialStress = 50)
        select(engine, "m1", "m2", "m3")

        engine.restart()

        assertEquals(50, engine.state.stress)
        assertEquals(0L, engine.state.musicSlowdownRemainingMillis)
    }

    private fun engine(
        initialStress: Int,
        degradationProbability: Double = StressConfig.DEFAULT_DEGRADATION_PROBABILITY,
        config: StressConfig = StressConfig(degradationProbability = degradationProbability),
    ) = DefaultGameEngine(
        FixedLevels.LEVEL_1.copy(board = Board(TILES)),
        initialStress = initialStress,
        stressConfig = config,
    )

    private fun select(
        engine: DefaultGameEngine,
        vararg ids: String,
    ) {
        for (id in ids) engine.selectTile(id)
    }

    private companion object {
        /** Four triples side by side on one layer, so every tile is selectable. */
        val TILES =
            listOf(
                "m1" to TileType.MUSIC,
                "m2" to TileType.MUSIC,
                "m3" to TileType.MUSIC,
                "c1" to TileType.COFFEE,
                "c2" to TileType.COFFEE,
                "c3" to TileType.COFFEE,
                "e1" to TileType.EXAM,
                "e2" to TileType.EXAM,
                "e3" to TileType.EXAM,
                "m4" to TileType.MUSIC,
                "m5" to TileType.MUSIC,
                "m6" to TileType.MUSIC,
            ).mapIndexed { index, (id, type) -> Tile(id, type, TilePosition(row = 0, column = index * 2, layer = 0)) }
    }
}
