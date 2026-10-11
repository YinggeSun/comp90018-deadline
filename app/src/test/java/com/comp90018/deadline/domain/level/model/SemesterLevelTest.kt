package com.comp90018.deadline.domain.level.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SemesterLevelTest {
    @Test
    fun thereAreSixLevelsCoveringTwelveWeeks() {
        assertEquals(6, SemesterLevel.ALL.size)
        assertEquals(1, SemesterLevel.ALL.first().firstWeek)
        assertEquals(SemesterDifficulty.SEMESTER_WEEKS, SemesterLevel.ALL.last().lastWeek)
    }

    @Test
    fun eachLevelStartsTheWeekAfterThePreviousOneEnds() {
        SemesterLevel.ALL.zipWithNext().forEach { (previous, next) ->
            assertEquals(previous.lastWeek + 1, next.firstWeek)
        }
    }

    @Test
    fun idsMatchTheGeneratorAndCanBeLookedUp() {
        SemesterLevel.ALL.forEach { level ->
            assertEquals("level_${level.number}", level.id)
            assertEquals(level, SemesterLevel.fromId(level.id))
        }
        assertNull(SemesterLevel.fromId("level_7"))
        assertNull(SemesterLevel.fromId("sample_level"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun levelNumbersOutsideTheSemesterAreRejected() {
        SemesterLevel(7)
    }
}
