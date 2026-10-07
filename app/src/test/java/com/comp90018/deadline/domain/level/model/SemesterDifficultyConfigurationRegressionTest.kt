package com.comp90018.deadline.domain.level.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Explicit tuning contract: intentional balancing changes must update this table. */
@RunWith(Parameterized::class)
class SemesterDifficultyConfigurationRegressionTest(
    private val week: Int,
    private val rows: Int,
    private val columns: Int,
    private val tileCount: Int,
    private val maxLayer: Int,
) {
    @Test
    fun weekHasExpectedDifficultyConfiguration() {
        val actual = SemesterDifficulty.forWeek(week)
        val expected = LevelConfig(LayoutTemplate(rows, columns), tileCount, maxLayer)
        assertEquals("week=$week", week, actual.week)
        assertEquals("week=$week", listOf(expected), actual.levels)
        assertEquals("semester table week=$week", actual, SemesterDifficulty.weeks[week - 1])
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "week={0}")
        fun configurations(): List<Array<Int>> =
            listOf(
                arrayOf(1, 3, 3, 9, 0),
                arrayOf(2, 3, 3, 9, 0),
                arrayOf(3, 3, 3, 9, 0),
                arrayOf(4, 4, 4, 12, 1),
                arrayOf(5, 4, 4, 12, 1),
                arrayOf(6, 4, 4, 12, 1),
                arrayOf(7, 5, 5, 18, 2),
                arrayOf(8, 5, 5, 18, 2),
                arrayOf(9, 5, 5, 18, 2),
                arrayOf(10, 6, 6, 24, 3),
                arrayOf(11, 6, 6, 24, 3),
                arrayOf(12, 6, 6, 24, 3),
            )
    }
}
