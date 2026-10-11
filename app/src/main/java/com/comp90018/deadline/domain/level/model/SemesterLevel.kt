package com.comp90018.deadline.domain.level.model

/**
 * One of the playable levels, described without generating its board. Each level covers
 * [SemesterDifficulty.WEEKS_PER_LEVEL] semester weeks; its board is generated when the
 * level starts.
 */
data class SemesterLevel(val number: Int) {
    init {
        require(number in 1..SemesterDifficulty.TOTAL_LEVELS) {
            "Level must be between 1 and ${SemesterDifficulty.TOTAL_LEVELS}."
        }
    }

    /** Matches the ID the generator gives the level. */
    val id: String get() = "$ID_PREFIX$number"

    val name: String get() = "Level $number"

    val firstWeek: Int get() = SemesterDifficulty.firstWeekForLevel(number)

    val lastWeek: Int get() = SemesterDifficulty.lastWeekForLevel(number)

    private val config: LevelConfig get() = SemesterDifficulty.forLevel(number).levels.single()

    val tileCount: Int get() = config.tileCount

    /** Layers the generator may use; [LevelConfig.maxLayer] is the top layer index. */
    val layerCount: Int get() = config.maxLayer + 1

    companion object {
        private const val ID_PREFIX = "level_"

        /** Every playable level, in order. */
        val ALL: List<SemesterLevel> = (1..SemesterDifficulty.TOTAL_LEVELS).map(::SemesterLevel)

        fun fromId(levelId: String): SemesterLevel? = ALL.find { it.id == levelId }
    }
}
