package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.data.sync.ConflictResolver
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressFieldsTest {
    @Test
    fun obsoleteCloudLevelIdsAreNotRestoredButSemesterWeeksRemainValid() {
        val fields =
            mapOf(
                ProgressFields.COMPLETED to listOf("level_6", "level_7", "level_12"),
                ProgressFields.WEEK to 12L,
                ProgressFields.BESTS to
                    mapOf("level_7" to mapOf(ProgressFields.TIME to 30_000L, ProgressFields.ACHIEVED_AT to 1L)),
            )
        val remote = ProgressFields.read(fields::get)
        val merged = ConflictResolver.merge(PlayerProgress(), remote)
        assertEquals(setOf("level_6"), merged.completedLevelIds)
        assertTrue(merged.personalBests.isEmpty())
        assertEquals(12, merged.highestUnlockedWeek)
        assertEquals(merged, ConflictResolver.merge(merged, remote))
    }

    @Test
    fun cloudWritesOnlyPublishedLevelsWithoutMutatingLocalProgress() {
        val local =
            PlayerProgress(
                completedLevelIds = setOf("level_6", "level_7", "local_1"),
                highestUnlockedWeek = 12,
                personalBests =
                    mapOf(
                        "level_6" to PersonalBest("level_6", 1_000, 1),
                        "level_7" to PersonalBest("level_7", 2_000, 2),
                        "local_1" to PersonalBest("local_1", 3_000, 3),
                    ),
                lastModifiedMillis = 100,
            )
        val fields = ProgressFields.write(local)
        assertEquals(listOf("level_6"), fields[ProgressFields.COMPLETED])
        assertEquals(
            mapOf("level_6" to mapOf(ProgressFields.TIME to 1_000L, ProgressFields.ACHIEVED_AT to 1L)),
            fields[ProgressFields.BESTS],
        )
        assertEquals(12, fields[ProgressFields.WEEK])
        assertEquals(100L, fields[ProgressFields.LAST_MODIFIED])
        assertEquals(setOf("level_6", "level_7", "local_1"), local.completedLevelIds)
        assertEquals(local.completedLevelIds, local.personalBests.keys)
    }
}
