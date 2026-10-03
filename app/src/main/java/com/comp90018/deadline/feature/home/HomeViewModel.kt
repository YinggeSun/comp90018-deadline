package com.comp90018.deadline.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Home screen state. Progress is not persisted yet (#35 / #36), so by
 * default there is no level to continue; [continueLevelId] is the seam for
 * the progress repository once it exists.
 */
class HomeViewModel(
    continueLevelId: () -> String? = { null }
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(continueLevelId = continueLevelId()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
}
