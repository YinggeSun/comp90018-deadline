
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
                // Level 1: Weeks 1–2
                arrayOf(1, 4, 4, 18, 1, 3),
                arrayOf(2, 4, 4, 18, 1, 3),
                // Level 2: Weeks 3–4
                arrayOf(3, 4, 4, 27, 1, 5),
                arrayOf(4, 4, 4, 27, 1, 5),
                // Level 3: Weeks 5–6
                arrayOf(5, 5, 5, 36, 2, 8),
                arrayOf(6, 5, 5, 36, 2, 8),
                // Level 4: Weeks 7–8
                arrayOf(7, 5, 5, 45, 3, 8),
                arrayOf(8, 5, 5, 45, 3, 8),
                // Level 5: Weeks 9–10
                arrayOf(9, 6, 6, 54, 4, 9),
                arrayOf(10, 6, 6, 54, 4, 9),
                // Level 6: Weeks 11–12
                arrayOf(11, 6, 6, 63, 5, 10),
                arrayOf(12, 6, 6, 63, 5, 10),
            )
    }
}
