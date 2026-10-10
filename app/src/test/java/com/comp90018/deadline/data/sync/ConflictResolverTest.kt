package com.comp90018.deadline.data.sync

import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolverTest {
    private fun best(
        level: String,
        time: Long,
        at: Long = 0,
    ) = level to PersonalBest(level, time, at)

    private val phone =
        PlayerProgress(
            completedLevelIds = setOf("level_1", "level_2"),
            highestUnlockedWeek = 5,
            personalBests = mapOf(best("level_1", 40_000), best("level_2", 90_000)),
            lastModifiedMillis = 100,
        )
    private val cloud =
        PlayerProgress(
            completedLevelIds = setOf("level_1", "level_3"),
            highestUnlockedWeek = 7,
            personalBests = mapOf(best("level_1", 30_000), best("level_3", 120_000)),
            lastModifiedMillis = 200,
        )

    @Test
    fun completedLevelsAreCombined() {
        assertEquals(setOf("level_1", "level_2", "level_3"), ConflictResolver.merge(phone, cloud).completedLevelIds)
    }

    @Test
    fun furthestUnlockedWeekWins() {
        assertEquals(7, ConflictResolver.merge(phone, cloud).highestUnlockedWeek)
    }

    @Test
    fun fasterBestWinsPerLevelAndOneSidedBestsAreKept() {
        val merged = ConflictResolver.merge(phone, cloud).personalBests

        assertEquals(30_000L, merged["level_1"]?.timeMillis)
        assertEquals(90_000L, merged["level_2"]?.timeMillis)
        assertEquals(120_000L, merged["level_3"]?.timeMillis)
    }

    @Test
    fun equalTimesKeepTheEarlierRecord() {
        val early = PlayerProgress(personalBests = mapOf(best("level_1", 30_000, at = 10)))
        val late = PlayerProgress(personalBests = mapOf(best("level_1", 30_000, at = 99)))

        assertEquals(10L, ConflictResolver.merge(early, late).bestFor("level_1")?.achievedAtMillis)
        assertEquals(10L, ConflictResolver.merge(late, early).bestFor("level_1")?.achievedAtMillis)
    }

    @Test
    fun latestModificationTimeIsKept() {
        assertEquals(200L, ConflictResolver.merge(phone, cloud).lastModifiedMillis)
    }

    @Test
    fun mergeIsOrderIndependentRepeatableAndGroupable() {
        val third = PlayerProgress(highestUnlockedWeek = 3, personalBests = mapOf(best("level_2", 60_000)))

        assertEquals(ConflictResolver.merge(phone, cloud), ConflictResolver.merge(cloud, phone))
        assertEquals(ConflictResolver.merge(phone, cloud), ConflictResolver.merge(ConflictResolver.merge(phone, cloud), cloud))
        assertEquals(
            ConflictResolver.merge(ConflictResolver.merge(phone, cloud), third),
            ConflictResolver.merge(phone, ConflictResolver.merge(cloud, third)),
        )
    }

    @Test
    fun mergeNeverLosesAnythingEitherSideHad() {
        val merged = ConflictResolver.merge(phone, cloud)

        for (side in listOf(phone, cloud)) {
            assertTrue(merged.completedLevelIds.containsAll(side.completedLevelIds))
            assertTrue(merged.highestUnlockedWeek >= side.highestUnlockedWeek)
            side.personalBests.forEach { (level, best) ->
                assertTrue(merged.bestFor(level)!!.timeMillis <= best.timeMillis)
            }
        }
    }

    @Test
    fun mergingWithFreshProgressChangesNothing() {
        assertEquals(phone, ConflictResolver.merge(phone, PlayerProgress()))
    }
}
