package com.comp90018.deadline.feature.levelselect

/** One row on the Level Select screen. */
data class LevelItemUiModel(
    val id: String,
    val name: String,
    val tileCount: Int,
    val layerCount: Int,
    val isLocked: Boolean,
    /** Personal Best completion time, or null when the level has not been completed. */
    val bestTimeSeconds: Long?
)

data class LevelSelectUiState(val levels: List<LevelItemUiModel> = emptyList())
