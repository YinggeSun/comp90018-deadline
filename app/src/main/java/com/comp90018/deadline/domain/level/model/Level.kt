package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.model.Board

/**
 * Complete definition of a playable level.
 *
 * A level contains the initial board layout and the configuration
 * controlling the rules and difficulty of that level. [week] is the Semester Week the
 * level belongs to; it drives unlocking and the week's Stress rate.
 */
data class Level(
    val id: String,
    val name: String,
    val board: Board,
    val config: LevelConfig,
    val week: Int = SemesterDifficulty.FIRST_WEEK
) {
    init {
        require(week in SemesterDifficulty.FIRST_WEEK..SemesterDifficulty.SEMESTER_WEEKS) {
            "Week must be within the semester."
        }
    }
}
