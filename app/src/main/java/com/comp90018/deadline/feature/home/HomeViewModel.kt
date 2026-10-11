package com.comp90018.deadline.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Home screen state. Continue points at the first unlocked level the player has not
 * cleared yet, and is hidden for a new player or once every unlocked level is cleared.
 */
class HomeViewModel(
    private val levels: List<SemesterLevel> = SemesterLevel.ALL,
    progress: Flow<PlayerProgress> = flowOf(PlayerProgress()),
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        progress
            .map(::toUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(PlayerProgress()))

    private fun toUiState(progress: PlayerProgress) =
        HomeUiState(
            continueLevelId =
                if (progress.completedLevelIds.isEmpty()) {
                    null
                } else {
                    levels.firstOrNull {
                        progress.isWeekUnlocked(it.firstWeek) && it.id !in progress.completedLevelIds
                    }?.id
                },
        )

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    HomeViewModel(progress = container.progressRepository.progress)
                }
            }
    }
}
