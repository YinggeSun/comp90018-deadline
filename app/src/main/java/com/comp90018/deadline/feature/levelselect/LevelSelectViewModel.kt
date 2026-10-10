package com.comp90018.deadline.feature.levelselect

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
 * Lists the playable levels with their weeks, lock state and Personal Best, kept current
 * from [progress]. A level is locked until its first Semester Week is unlocked. Boards are
 * generated only when a level starts, so the list needs no boards. Until [progress]
 * first emits, the state is loading rather than showing fresh-player locks.
 */
class LevelSelectViewModel(
    private val levels: List<SemesterLevel> = SemesterLevel.ALL,
    progress: Flow<PlayerProgress> = flowOf(PlayerProgress()),
) : ViewModel() {
    val uiState: StateFlow<LevelSelectUiState> =
        progress
            .map(::toUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LevelSelectUiState(isLoading = true))

    private fun toUiState(progress: PlayerProgress) =
        LevelSelectUiState(
            levels =
                levels.map { level ->
                    LevelItemUiModel(
                        id = level.id,
                        name = level.name,
                        firstWeek = level.firstWeek,
                        lastWeek = level.lastWeek,
                        tileCount = level.tileCount,
                        layerCount = level.layerCount,
                        isLocked = !progress.isWeekUnlocked(level.firstWeek),
                        bestTimeSeconds = progress.bestFor(level.id)?.let { it.timeMillis / MILLIS_PER_SECOND },
                    )
                },
        )

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    LevelSelectViewModel(progress = container.progressRepository.progress)
                }
            }
    }
}
