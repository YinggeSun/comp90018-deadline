package com.comp90018.deadline.feature.levelselect

/** One row on the Level Select screen. */
data class LevelItemUiModel(
    val id: String,
    val name: String,
    /** The semester weeks the level covers, e.g. 1 and 2. */
    val firstWeek: Int,
    val lastWeek: Int,
    val tileCount: Int,
    val layerCount: Int,
    val isLocked: Boolean,
    /** Personal Best completion time, or null when the level has not been completed. */
    val bestTimeSeconds: Long?,
)

/** [isLoading] is true until stored progress has been read, so lock states are never guessed. */
data class LevelSelectUiState(
    val levels: List<LevelItemUiModel> = emptyList(),
    val isLoading: Boolean = false,
)
