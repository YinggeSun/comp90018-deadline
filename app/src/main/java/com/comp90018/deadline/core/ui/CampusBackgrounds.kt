package com.comp90018.deadline.core.ui

import androidx.annotation.DrawableRes
import com.comp90018.deadline.R
import com.comp90018.deadline.domain.level.model.SemesterLevel

/**
 * University of Melbourne campus photos used as backgrounds: one for Home and one for each
 * level, so the levels feel like moving through a semester. Sources are listed in README.
 */
object CampusBackgrounds {
    @DrawableRes
    val home: Int = R.drawable.bg_home

    /** Level 1 to 6, in order. */
    private val levels =
        listOf(
            R.drawable.bg_level_1, // sandstone heritage building, start of semester
            R.drawable.bg_level_2, // Raymond Priestley Building arches
            R.drawable.bg_level_3, // arched walkway
            R.drawable.bg_level_4, // sports centre and running track
            R.drawable.bg_level_5, // library staircase at night
            R.drawable.bg_level_6, // Old Arts clock tower, exam weeks
        )

    /** The photo for the level with [levelId], or null for a level without one. */
    @DrawableRes
    fun forLevel(levelId: String): Int? = SemesterLevel.fromId(levelId)?.let { levels.getOrNull(it.number - 1) }
}
