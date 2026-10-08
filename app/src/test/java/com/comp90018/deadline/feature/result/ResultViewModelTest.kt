package com.comp90018.deadline.feature.result

import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultViewModelTest {
    private fun state(
        levelId: String = FixedLevels.LEVEL_1.id,
        won: Boolean = true,
        elapsedMillis: Long = 60_000,
        previousBest: Long? = null,
    ) = ResultViewModel(levelId, won, elapsedMillis, previousBest).uiState.value

    @Test
    fun showsOutcomeTimeAndLevelName() {
        val result = state(won = true, elapsedMillis = 83_000)

        assertTrue(result.won)
        assertEquals(83_000L, result.elapsedMillis)
        assertEquals(FixedLevels.LEVEL_1.name, result.levelName)
        assertEquals(FixedLevels.LEVEL_1.id, result.levelId)
    }

    @Test
    fun winOffersFollowingLevel() {
        assertEquals(FixedLevels.LEVEL_2.id, state(levelId = FixedLevels.LEVEL_1.id).nextLevelId)
        assertEquals(FixedLevels.LEVEL_3.id, state(levelId = FixedLevels.LEVEL_2.id).nextLevelId)
    }

    @Test
    fun lastLevelHasNoNextLevel() {
        assertNull(state(levelId = FixedLevels.ALL_LEVELS.last().id).nextLevelId)
    }

    @Test
    fun lossHasNoNextLevel() {
        assertNull(state(won = false).nextLevelId)
    }

    @Test
    fun unknownLevelHasNoNameOrNextLevel() {
        val result = state(levelId = "missing")

        assertEquals("", result.levelName)
        assertNull(result.nextLevelId)
    }

    @Test
    fun firstWinIsNewBestAndShowsThisTime() {
        val result = state(elapsedMillis = 42_000, previousBest = null)

        assertTrue(result.isNewBest)
        assertEquals(42_000L, result.bestTimeMillis)
    }

    @Test
    fun fasterWinIsNewBestAndShowsThisTime() {
        val result = state(elapsedMillis = 50_000, previousBest = 60_000)

        assertTrue(result.isNewBest)
        assertEquals(50_000L, result.bestTimeMillis)
    }

    @Test
    fun slowerWinKeepsPreviousBest() {
        val result = state(elapsedMillis = 70_000, previousBest = 60_000)

        assertFalse(result.isNewBest)
        assertEquals(60_000L, result.bestTimeMillis)
    }

    @Test
    fun equalTimeIsNotNewBest() {
        val result = state(elapsedMillis = 60_000, previousBest = 60_000)

        assertFalse(result.isNewBest)
        assertEquals(60_000L, result.bestTimeMillis)
    }

    @Test
    fun lossKeepsPreviousBestEvenWhenFaster() {
        val withBest = state(won = false, elapsedMillis = 10_000, previousBest = 60_000)
        val withoutBest = state(won = false, elapsedMillis = 10_000, previousBest = null)

        assertFalse(withBest.isNewBest)
        assertEquals(60_000L, withBest.bestTimeMillis)
        assertFalse(withoutBest.isNewBest)
        assertNull(withoutBest.bestTimeMillis)
    }
}
