package com.comp90018.deadline.feature.result

/**
 * Everything the Result screen shows. [bestTimeMillis] is the Personal Best
 * before this game, or null when there is none; [isNewBest] is true only
 * when this win beat an existing best. [nextLevelId] is set only after a
 * win with a following level.
 */
data class ResultUiState(
    val levelId: String = "",
    val levelName: String = "",
    val won: Boolean = false,
    val elapsedMillis: Long = 0L,
    val bestTimeMillis: Long? = null,
    val isNewBest: Boolean = false,
    val nextLevelId: String? = null
)
