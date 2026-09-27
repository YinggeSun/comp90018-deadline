package com.comp90018.deadline.domain.level.model

/**
 * Groups level configurations by semester week.
 */
data class WeekConfig(
    val week: Int,
    val levels: List<LevelConfig> = emptyList()
) {
    init {
        require(week > 0) {
            "Week must be positive."
        }
    }
}
