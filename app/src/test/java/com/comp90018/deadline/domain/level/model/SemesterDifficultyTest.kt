package com.comp90018.deadline.domain.level.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterDifficultyTest {
    @Test
    fun semester_containsTwelveWeeks() {
        assertEquals(12, SemesterDifficulty.weeks.size)
        assertEquals((1..12).toList(), SemesterDifficulty.weeks.map { it.week })
    }

    @Test
    fun laterWeeks_haveHarderConfigurations() {
        val week1 = SemesterDifficulty.forWeek(1).levels.first()
        val week12 = SemesterDifficulty.forWeek(12).levels.first()

        assertTrue(week12.tileCount > week1.tileCount)
        assertTrue(week12.maxLayer > week1.maxLayer)
        assertTrue(week12.layout.rows >= week1.layout.rows)
        assertTrue(week12.layout.columns >= week1.layout.columns)
    }

    @Test
    fun difficulty_progressesAcrossSemester() {
        val week1 = SemesterDifficulty.forWeek(1).levels.first()
        val week4 = SemesterDifficulty.forWeek(4).levels.first()
        val week7 = SemesterDifficulty.forWeek(7).levels.first()
        val week10 = SemesterDifficulty.forWeek(10).levels.first()

        assertTrue(week4.tileCount > week1.tileCount)
        assertTrue(week7.tileCount > week4.tileCount)
        assertTrue(week10.tileCount > week7.tileCount)

        assertTrue(week4.maxLayer > week1.maxLayer)
        assertTrue(week7.maxLayer > week4.maxLayer)
        assertTrue(week10.maxLayer > week7.maxLayer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weekBelowOne_isRejected() {
        SemesterDifficulty.forWeek(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weekAboveTwelve_isRejected() {
        SemesterDifficulty.forWeek(13)
    }

    @Test
    fun tileVariety_increasesAcrossSemester() {
        val expectedVarieties =
            listOf(
                3,
                3,
                3,
                4,
                4,
                4,
                5,
                5,
                5,
                6,
                6,
                6,
            )
        assertEquals(
            expectedVarieties,
            SemesterDifficulty.weeks.map { it.levels.single().tileVariety },
        )
    }
}
