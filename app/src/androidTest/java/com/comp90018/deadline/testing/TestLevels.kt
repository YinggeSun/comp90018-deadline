package com.comp90018.deadline.testing

import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.SemesterLevel

/**
 * Predictable boards for UI tests. Every level plays the small fixed Level 1 board (six
 * tiles on one layer, all selectable), under the requested level's ID, name and week, so a
 * test can win any level by tapping each tile in [board] order.
 */
object TestLevels {
    fun board(levelId: String): Level? =
        SemesterLevel.fromId(levelId)?.let { level ->
            FixedLevels.LEVEL_1.copy(id = level.id, name = level.name, week = level.firstWeek)
        }

    val source = LevelSource { board(it) }
}
