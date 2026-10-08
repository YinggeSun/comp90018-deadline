
package com.comp90018.deadline.domain.level.model

object SemesterDifficulty {

    const val FIRST_WEEK = 1
    const val SEMESTER_WEEKS = 12
    const val WEEKS_PER_LEVEL = 2
    const val TOTAL_LEVELS = 6


    val levels: List<WeekConfig> =
        (1..TOTAL_LEVELS).map { level ->
            WeekConfig(
                week = firstWeekForLevel(level),
                levels = listOf(configForLevel(level))
            )
        }
    val weeks: List<WeekConfig> =
        (FIRST_WEEK..SEMESTER_WEEKS).map { week ->
            WeekConfig(
                week = week,
                levels = listOf(
                    configForLevel(levelForWeek(week))
                )
            )
        }

    fun forWeek(week: Int): WeekConfig {
        require(week in FIRST_WEEK..SEMESTER_WEEKS) {
            "Week must be between 1 and $SEMESTER_WEEKS."
        }
        return weeks[week - FIRST_WEEK]
    }

    fun forLevel(level: Int): WeekConfig {
        require(level in 1..TOTAL_LEVELS) {
            "Level must be between 1 and $TOTAL_LEVELS."
        }
        return levels[level - 1]
    }

    fun levelForWeek(week: Int): Int {
        require(week in FIRST_WEEK..SEMESTER_WEEKS) {
            "Invalid semester week: $week"
        }
        return (week - FIRST_WEEK) / WEEKS_PER_LEVEL + 1
    }

    fun firstWeekForLevel(level: Int): Int {
        require(level in 1..TOTAL_LEVELS) {
            "Invalid level: $level"
        }
        return (level - 1) * WEEKS_PER_LEVEL + FIRST_WEEK
    }

    fun lastWeekForLevel(level: Int): Int =
        firstWeekForLevel(level) + WEEKS_PER_LEVEL - 1

    private fun configForLevel(level: Int): LevelConfig =
        when (level) {
            1 -> LevelConfig(
                layout = LayoutTemplate(4, 4),
                tileCount = 18,
                maxLayer = 1,
                tileVariety = 3
            )

            2 -> LevelConfig(
                layout = LayoutTemplate(4, 4),
                tileCount = 24,
                maxLayer = 1,
                tileVariety = 4
            )

            3 -> LevelConfig(
                layout = LayoutTemplate(5, 5),
                tileCount = 30,
                maxLayer = 2,
                tileVariety = 5
            )

            4 -> LevelConfig(
                layout = LayoutTemplate(5, 5),
                tileCount = 36,
                maxLayer = 3,
                tileVariety = 5
            )

            5 -> LevelConfig(
                layout = LayoutTemplate(6, 6),
                tileCount = 42,
                maxLayer = 4,
                tileVariety = 6
            )

            6 -> LevelConfig(
                layout = LayoutTemplate(6, 6),
                tileCount = 48,
                maxLayer = 5,
                tileVariety = 6
            )

            else -> throw IllegalArgumentException(
                "Invalid level: $level"
            )
        }
}
