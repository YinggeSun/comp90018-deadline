
package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.model.Board

data class Level(
    val id: String,
    val name: String,
    val board: Board,
    val config: LevelConfig,
    val week: Int = SemesterDifficulty.FIRST_WEEK,
) {
    init {
        require(
            week in SemesterDifficulty.FIRST_WEEK..SemesterDifficulty.SEMESTER_WEEKS,
        ) {
            "Week must be within the semester."
        }
    }

    val levelNumber: Int
        get() = SemesterDifficulty.levelForWeek(week)
}
