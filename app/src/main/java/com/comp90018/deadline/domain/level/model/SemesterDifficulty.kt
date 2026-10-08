package com.comp90018.deadline.domain.level.model

/**
 * Defines the difficulty progression across the 12 semester weeks.
 *
 * Difficulty increases through board size, tile count and layer count.
 * The values are kept outside the UI so they can be adjusted independently.
 */
object SemesterDifficulty {
<<<<<<< HEAD
    const val FIRST_WEEK = 1

    const val SEMESTER_WEEKS = 12

    val weeks: List<WeekConfig> =
        (1..SEMESTER_WEEKS).map { week ->
            WeekConfig(
                week = week,
                levels = listOf(configForWeek(week)),
            )
        }
=======

    const val SEMESTER_WEEKS = 12

    val weeks: List<WeekConfig> = (1..SEMESTER_WEEKS).map { week ->
        WeekConfig(
            week = week,
            levels = listOf(configForWeek(week))
        )
    }
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4

    fun forWeek(week: Int): WeekConfig {
        require(week in 1..SEMESTER_WEEKS) {
            "Week must be between 1 and $SEMESTER_WEEKS."
        }

        return weeks[week - 1]
    }

    private fun configForWeek(week: Int): LevelConfig =
        when (week) {
<<<<<<< HEAD
            in 1..3 ->
                LevelConfig(
                    layout = LayoutTemplate(rows = 3, columns = 3),
                    tileCount = 9,
                    maxLayer = 0,
                )

            in 4..6 ->
                LevelConfig(
                    layout = LayoutTemplate(rows = 4, columns = 4),
                    tileCount = 12,
                    maxLayer = 1,
                )

            in 7..9 ->
                LevelConfig(
                    layout = LayoutTemplate(rows = 5, columns = 5),
                    tileCount = 18,
                    maxLayer = 2,
                )

            else ->
                LevelConfig(
                    layout = LayoutTemplate(rows = 6, columns = 6),
                    tileCount = 24,
                    maxLayer = 3,
                )
=======
            in 1..3 -> LevelConfig(
                layout = LayoutTemplate(rows = 3, columns = 3),
                tileCount = 9,
                maxLayer = 0
            )

            in 4..6 -> LevelConfig(
                layout = LayoutTemplate(rows = 4, columns = 4),
                tileCount = 12,
                maxLayer = 1
            )

            in 7..9 -> LevelConfig(
                layout = LayoutTemplate(rows = 5, columns = 5),
                tileCount = 18,
                maxLayer = 2
            )

            else -> LevelConfig(
                layout = LayoutTemplate(rows = 6, columns = 6),
                tileCount = 24,
                maxLayer = 3
            )
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
        }
}
