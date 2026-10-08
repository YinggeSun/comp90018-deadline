
package com.comp90018.deadline.domain.level.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SemesterDifficultyConfigurationRegressionTest(
    private val week: Int,
    private val rows: Int,
    private val columns: Int,
    private val tileCount: Int,
    private val maxLayer: Int,
    private val tileVariety: Int,
) {
    @Test
    fun weekHasExpectedDifficultyConfiguration() {
        val actual = SemesterDifficulty.forWeek(week)

        val expected =
            LevelConfig(
                layout =
                    LayoutTemplate(
                        rows = rows,
                        columns = columns,
                    ),
                tileCount = tileCount,
                maxLayer = maxLayer,
                tileVariety = tileVariety,
            )

        assertEquals(
            "week=$week",
            week,
            actual.week,
        )

        assertEquals(
            "week=$week",
            listOf(expected),
            actual.levels,
        )

        assertEquals(
            "semester table week=$week",
            actual,
            SemesterDifficulty.weeks[week - 1],
        )
    }

    @Test
    fun weekMapsToCorrectBiweeklyLevel() {
        val expectedLevel = (week + 1) / 2

        assertEquals(
            "level mapping for week=$week",
            expectedLevel,
            SemesterDifficulty.levelForWeek(week),
        )

        val levelConfig =
            SemesterDifficulty.forLevel(expectedLevel)

        assertEquals(
            "level config for week=$week",
            SemesterDifficulty.forWeek(week).levels.single(),
            levelConfig.levels.single(),
        )

        assertEquals(
            "first week for level=$expectedLevel",
            (expectedLevel - 1) * 2 + 1,
            levelConfig.week,
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "week={0}")
        fun configurations(): List<Array<Int>> =
            listOf(
                arrayOf(1, 4, 4, 18, 1, 3),
                arrayOf(2, 4, 4, 18, 1, 3),
                arrayOf(3, 4, 4, 24, 1, 4),
                arrayOf(4, 4, 4, 24, 1, 4),
                arrayOf(5, 5, 5, 30, 2, 5),
                arrayOf(6, 5, 5, 30, 2, 5),
                arrayOf(7, 5, 5, 36, 3, 5),
                arrayOf(8, 5, 5, 36, 3, 5),
                arrayOf(9, 6, 6, 42, 4, 6),
                arrayOf(10, 6, 6, 42, 4, 6),
                arrayOf(11, 6, 6, 48, 5, 6),
                arrayOf(12, 6, 6, 48, 5, 6),
            )
    }
}
