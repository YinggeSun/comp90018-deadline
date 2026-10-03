package com.comp90018.deadline.feature.home

/**
 * [continueLevelId] is the level to resume from Home, or null when there is
 * nothing to continue (the Continue button is then hidden).
 */
data class HomeUiState(val continueLevelId: String? = null)
