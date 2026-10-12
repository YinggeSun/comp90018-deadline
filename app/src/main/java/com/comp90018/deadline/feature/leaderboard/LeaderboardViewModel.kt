package com.comp90018.deadline.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.LeaderboardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Live per-level leaderboard. The ranking for the selected level is observed only while the
 * screen is shown, and switching level or pressing Retry starts a fresh observation. The
 * player's own entry is marked using [currentUserId].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardViewModel(
    private val repository: LeaderboardRepository,
    currentUserId: Flow<String?> = flowOf(null),
    initialLevelId: String = SemesterLevel.ALL.first().id,
    private val levels: List<SemesterLevel> = SemesterLevel.ALL,
) : ViewModel() {
    private val selectedLevelId = MutableStateFlow(initialLevelId)
    private val reloads = MutableStateFlow(0)

    private val tabs = levels.map { LeaderboardLevelTab(it.id, it.name) }

    /** The ranking together with the level it belongs to, so a stale one is never shown. */
    private val rankings: Flow<Pair<String, LeaderboardState>> =
        combine(selectedLevelId, reloads) { levelId, _ -> levelId }
            .flatMapLatest { levelId -> repository.observe(levelId).map { levelId to it } }

    val uiState: StateFlow<LeaderboardUiState> =
        combine(selectedLevelId, rankings, currentUserId.onStart { emit(null) }) { selected, (levelId, state), userId ->
            LeaderboardUiState(
                levels = tabs,
                selectedLevelId = selected,
                content = if (levelId == selected) state.toContent(userId) else LeaderboardContent.Loading,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            LeaderboardUiState(levels = tabs, selectedLevelId = initialLevelId),
        )

    fun selectLevel(levelId: String) {
        if (levels.any { it.id == levelId }) selectedLevelId.value = levelId
    }

    fun retry() {
        reloads.value++
    }

    companion object {
        /** Keeps the live ranking open through a screen rotation, but not much longer. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Opens on the furthest level the player has reached, where their newest times are. */
        fun initialLevelFor(
            progress: PlayerProgress,
            levels: List<SemesterLevel> = SemesterLevel.ALL,
        ): String = (levels.lastOrNull { progress.isWeekUnlocked(it.firstWeek) } ?: levels.first()).id

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    LeaderboardViewModel(
                        repository = container.leaderboardRepository,
                        currentUserId = container.authRepository.userId,
                        initialLevelId = initialLevelFor(container.currentProgress.value),
                    )
                }
            }
    }
}

private fun LeaderboardState.toContent(currentUserId: String?): LeaderboardContent =
    when (this) {
        LeaderboardState.Loading -> LeaderboardContent.Loading
        is LeaderboardState.Ranked -> LeaderboardContent.Ranked(entries.toRows(currentUserId))
        is LeaderboardState.Offline -> LeaderboardContent.Offline(cachedEntries.toRows(currentUserId))
        is LeaderboardState.Error -> LeaderboardContent.Error(reason)
    }

/** Entries arrive fastest first; equal times share the better rank (1, 2, 2, 4). */
private fun List<LeaderboardEntry>.toRows(currentUserId: String?): List<LeaderboardRowUiModel> {
    var rank = 0
    return mapIndexed { index, entry ->
        if (index == 0 || entry.timeMillis != this[index - 1].timeMillis) rank = index + 1
        LeaderboardRowUiModel(
            rank = rank,
            nickname = entry.nickname,
            timeMillis = entry.timeMillis,
            isCurrentPlayer = currentUserId != null && entry.userId == currentUserId,
        )
    }
}
