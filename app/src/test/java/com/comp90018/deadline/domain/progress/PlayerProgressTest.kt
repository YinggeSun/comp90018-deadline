package com.comp90018.deadline.domain.progress

import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProgressTest {

    private fun result(levelId: String = "level_1", week: Int = 1, time: Long = 60_000, at: Long = 1_000) =
        CompletionResult(levelId, week, time, at)

    @Test
    fun freshProgressUnlocksOnlyWeekOne() {
        val progress = PlayerProgress()

        assertTrue(progress.isWeekUnlocked(1))
        assertFalse(progress.isWeekUnlocked(2))
        assertNull(progress.bestFor("level_1"))
    }

    @Test
    fun firstClearSetsPersonalBestAndUnlocksNextWeek() {
        val (progress, outcome) = PlayerProgress().withCompletion(result())

        assertTrue(outcome.isNewPersonalBest)
        assertNull(outcome.previousBest)
        assertEquals(2, outcome.newlyUnlockedWeek)
        assertEquals(PersonalBest("level_1", 60_000, 1_000), progress.bestFor("level_1"))
        assertEquals(setOf("level_1"), progress.completedLevelIds)
        assertTrue(progress.isWeekUnlocked(2))
    }

    @Test
    fun fasterTimeReplacesPersonalBest() {
        val (first, _) = PlayerProgress().withCompletion(result(time = 60_000, at = 1_000))
        val (second, outcome) = first.withCompletion(result(time = 45_000, at = 2_000))

        assertTrue(outcome.isNewPersonalBest)
        assertEquals(60_000L, outcome.previousBest?.timeMillis)
        assertEquals(45_000L, second.bestFor("level_1")?.timeMillis)
    }

    @Test
    fun slowerTimeDoesNotOverwritePersonalBest() {
        val (first, _) = PlayerProgress().withCompletion(result(time = 45_000, at = 1_000))
        val (second, outcome) = first.withCompletion(result(time = 60_000, at = 2_000))

        assertFalse(outcome.isNewPersonalBest)
        assertEquals(PersonalBest("level_1", 45_000, 1_000), second.bestFor("level_1"))
    }

    @Test
    fun equalTimeKeepsOriginalPersonalBest() {
        val (first, _) = PlayerProgress().withCompletion(result(time = 45_000, at = 1_000))
        val (second, outcome) = first.withCompletion(result(time = 45_000, at = 2_000))

        assertFalse(outcome.isNewPersonalBest)
        assertEquals(1_000L, second.bestFor("level_1")?.achievedAtMillis)
    }

    @Test
    fun replayingEarlierWeekNeverLocksLaterWeeks() {
        val progress = PlayerProgress(highestUnlockedWeek = 5)
        val (updated, outcome) = progress.withCompletion(result(week = 1))

        assertEquals(5, updated.highestUnlockedWeek)
        assertNull(outcome.newlyUnlockedWeek)
    }

    @Test
    fun finalWeekDoesNotUnlockBeyondSemester() {
        val last = SemesterDifficulty.SEMESTER_WEEKS
        val progress = PlayerProgress(highestUnlockedWeek = last)
        val (updated, outcome) = progress.withCompletion(result(week = last))

        assertEquals(last, updated.highestUnlockedWeek)
        assertNull(outcome.newlyUnlockedWeek)
    }

    @Test
    fun lastModifiedNeverMovesBackwards() {
        val progress = PlayerProgress(lastModifiedMillis = 5_000)
        val (updated, _) = progress.withCompletion(result(at = 1_000))

        assertEquals(5_000L, updated.lastModifiedMillis)
    }

    @Test
    fun rejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) { PlayerProgress(highestUnlockedWeek = 0) }
        assertThrows(IllegalArgumentException::class.java) { PlayerProgress(highestUnlockedWeek = 13) }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerProgress(personalBests = mapOf("a" to PersonalBest("b", 1, 0)))
        }
        assertThrows(IllegalArgumentException::class.java) { result(time = 0) }
        assertThrows(IllegalArgumentException::class.java) { result(week = 0) }
    }
}
