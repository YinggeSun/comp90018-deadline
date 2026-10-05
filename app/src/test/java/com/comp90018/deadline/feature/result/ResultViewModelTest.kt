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
        best: Long? = null
    ) = ResultViewModel(levelId, won, elapsedMillis, bestTimeMillis = { best }).uiState.value

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
        assertNull(state(levelId = FixedLevels.LEVEL_3.id).nextLevelId)
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
    fun noPersonalBestByDefault() {
        val result = ResultViewModel(FixedLevels.LEVEL_1.id, won = true, elapsedMillis = 1_000).uiState.value

        assertNull(result.bestTimeMillis)
        assertFalse(result.isNewBest)
    }

    @Test
    fun fasterWinIsNewBest() {
        val result = state(elapsedMillis = 50_000, best = 60_000)

        assertEquals(60_000L, result.bestTimeMillis)
        assertTrue(result.isNewBest)
    }

    @Test
    fun slowerWinOrLossIsNotNewBest() {
        assertFalse(state(elapsedMillis = 70_000, best = 60_000).isNewBest)
        assertFalse(state(won = false, elapsedMillis = 10_000, best = 60_000).isNewBest)
    }
}
