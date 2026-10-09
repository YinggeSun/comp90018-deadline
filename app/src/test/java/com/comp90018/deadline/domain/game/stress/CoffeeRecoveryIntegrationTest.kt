package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coffee Recovery as the engine applies it: a Coffee triple is resolved like any other
 * triple and additionally lowers [com.comp90018.deadline.domain.game.model.GameState.stress].
 */
class CoffeeRecoveryIntegrationTest {
    @Test
    fun coffeeTripleClearsTheTrayAndLowersStressWhileOtherTriplesOnlyAccumulate() {
        val engine = engine(FixedLevels.LEVEL_1, config(baseRate = 10, recoveryBase = 25))

        select(engine, "level_1_coffee_1", "level_1_coffee_2")
        assertEquals(20, engine.state.stress)
        assertEquals(COFFEE_IDS.take(2), engine.state.taskTray.tiles.map { it.id })

        engine.selectTile("level_1_coffee_3")
        assertEquals(5, engine.state.stress)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertEquals(BOOK_IDS, engine.state.board.tiles.map { it.id })
        assertEquals(GameStatus.RUNNING, engine.state.status)

        select(engine, *BOOK_IDS.toTypedArray())
        // A Book triple accumulates like any other selection but never recovers.
        assertEquals(35, engine.state.stress)
        assertTrue(engine.state.board.tiles.isEmpty())
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertEquals(GameStatus.WON, engine.state.status)

        // A terminal game ignores further selections, so stress stops moving too.
        engine.selectTile("level_1_coffee_1")
        assertEquals(35, engine.state.stress)
    }

    @Test
    fun coffeeMatchLeavesBoardTrayAndAvailabilityConsistent() {
        val engine = engine(FixedLevels.LEVEL_3, config(recoveryBase = 25), initialStress = 60)
        val coffeeIds = listOf("level_3_coffee_1", "level_3_coffee_2", "level_3_coffee_3")
        val bookIds = listOf("level_3_book_1", "level_3_book_2", "level_3_book_3")

        // The top-layer laptops cover every coffee tile until they are cleared.
        for (id in coffeeIds) assertFalse("Must start covered: " + id, engine.isTileSelectable(id))

        select(engine, "level_3_laptop_1", "level_3_laptop_2", "level_3_laptop_3")
        assertEquals("A Laptop triple must not recover stress", 60, engine.state.stress)
        for (id in coffeeIds) assertTrue("Must be unlocked: " + id, engine.isTileSelectable(id))

        select(engine, *coffeeIds.toTypedArray())

        assertEquals(35, engine.state.stress)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertEquals(bookIds, engine.state.board.tiles.map { it.id })
        for (id in coffeeIds) assertFalse("Must be gone: " + id, engine.isTileSelectable(id))
        for (id in bookIds) assertTrue("Must remain playable: " + id, engine.isTileSelectable(id))
        assertEquals(GameStatus.RUNNING, engine.state.status)
    }

    @Test
    fun coffeeCannotPushStressBelowZero() {
        val engine = engine(FixedLevels.LEVEL_1, config(recoveryBase = 40), initialStress = 10)

        select(engine, *COFFEE_IDS.toTypedArray())

        assertEquals(0, engine.state.stress)
    }

    @Test
    fun coffeeRecoveryCanClearHighStress() {
        val stressConfig = config(recoveryBase = 20)
        val engine = engine(FixedLevels.LEVEL_1, stressConfig, initialStress = 80)
        val stressManager = StressManager(stressConfig)
        assertTrue(stressManager.isHighStress(engine.state.stress))

        select(engine, *COFFEE_IDS.toTypedArray())

        assertEquals(60, engine.state.stress)
        assertFalse(stressManager.isHighStress(engine.state.stress))
    }

    @Test
    fun recoveryAmountFollowsTheSemesterWeek() {
        val stressConfig = config(recoveryBase = 25, recoveryDecline = 2)

        for ((week, expected) in listOf(1 to 55, 5 to 63, 13 to 79)) {
            val engine = engine(FixedLevels.LEVEL_1, stressConfig, initialStress = 80, week = week)

            select(engine, *COFFEE_IDS.toTypedArray())

            assertEquals("Week " + week, expected, engine.state.stress)
        }
    }

    @Test
    fun startingStressIsRepairedIntoTheConfiguredRange() {
        val stressConfig = config(maximum = 50)

        assertEquals(50, engine(FixedLevels.LEVEL_1, stressConfig, initialStress = 500).state.stress)
        assertEquals(0, engine(FixedLevels.LEVEL_1, stressConfig, initialStress = -20).state.stress)
    }

    @Test
    fun rejectedSelectionsLeaveStressUntouched() {
        val engine = engine(FixedLevels.LEVEL_3, config(baseRate = 7), initialStress = 20)

        for (invalid in listOf("level_3_coffee_1", "unknown-tile")) {
            engine.selectTile(invalid)
            assertEquals(20, engine.state.stress)
        }
    }

    @Test
    fun undoRestoresStressUntilACoffeeMatchCommitsIt() {
        val engine = engine(FixedLevels.LEVEL_1, config(baseRate = 5, recoveryBase = 25))

        engine.selectTile("level_1_coffee_1")
        assertEquals(5, engine.state.stress)
        engine.selectTile("level_1_coffee_2")
        assertEquals(10, engine.state.stress)

        engine.undo()
        assertEquals(5, engine.state.stress)
        assertEquals(listOf("level_1_coffee_1"), engine.state.taskTray.tiles.map { it.id })

        select(engine, "level_1_coffee_2", "level_1_coffee_3")
        assertEquals(0, engine.state.stress)

        // A completed match commits its tiles and clears undo history, recovery included.
        engine.undo()
        assertEquals(0, engine.state.stress)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
    }

    @Test
    fun restartReturnsStressToTheStartingValue() {
        val engine =
            engine(
                FixedLevels.LEVEL_1,
                config(baseRate = 5, recoveryBase = 25),
                initialStress = 30,
            )

        select(engine, *COFFEE_IDS.toTypedArray())
        assertEquals(20, engine.state.stress)

        engine.restart()

        assertEquals(30, engine.state.stress)
        assertEquals(FixedLevels.LEVEL_1.board, engine.state.board)
        assertTrue(engine.state.taskTray.tiles.isEmpty())
        assertEquals(GameStatus.RUNNING, engine.state.status)
    }

    @Test
    fun defaultEngineExposesAndAppliesTheDefaultStressTuning() {
        val engine = DefaultGameEngine(FixedLevels.LEVEL_1)

        assertEquals(StressConfig(), engine.stressConfig)
        assertEquals(0, engine.state.stress)

        select(engine, *COFFEE_IDS.toTypedArray())

        // Three default accumulation steps are far smaller than one default recovery.
        assertEquals(0, engine.state.stress)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveWeek() {
        DefaultGameEngine(FixedLevels.LEVEL_1, week = 0)
    }

    private fun engine(
        level: Level,
        stressConfig: StressConfig,
        initialStress: Int = 0,
        week: Int = 1,
    ): GameEngine =
        DefaultGameEngine(
            level = level,
            week = week,
            stressConfig = stressConfig,
            initialStress = initialStress,
        )

    /** Flat rates by default so each test reads one rule at a time. */
    private fun config(
        maximum: Int = 100,
        baseRate: Int = 0,
        recoveryBase: Int = StressConfig.DEFAULT_COFFEE_RECOVERY_BASE,
        recoveryDecline: Int = 0,
    ) = StressConfig(
        maximum = maximum,
        baseRate = baseRate,
        rateGrowthPerWeek = 0,
        coffeeRecoveryBase = recoveryBase,
        coffeeRecoveryDeclinePerWeek = recoveryDecline,
    )

    private fun select(
        engine: GameEngine,
        vararg tileIds: String,
    ) {
        for (id in tileIds) engine.selectTile(id)
    }

    private companion object {
        val COFFEE_IDS = listOf("level_1_coffee_1", "level_1_coffee_2", "level_1_coffee_3")
        val BOOK_IDS = listOf("level_1_book_1", "level_1_book_2", "level_1_book_3")
    }
}
