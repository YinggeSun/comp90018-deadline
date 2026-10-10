package com.comp90018.deadline.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where preparing a level's board has got to. */
sealed interface GameLoadState {
    data object Loading : GameLoadState

    data class Ready(val level: Level) : GameLoadState

    /** No level has this ID, or its board could not be prepared. */
    data object Failed : GameLoadState
}

/**
 * Prepares the board for one visit to the Game screen. Each visit (including Retry and
 * Next Level, which open a new Game screen) gets its own instance and so a new board;
 * the board is kept across configuration changes while the screen is open.
 */
class GameLoadViewModel(
    private val levelId: String,
    private val source: LevelSource,
) : ViewModel() {
    private val _state = MutableStateFlow<GameLoadState>(GameLoadState.Loading)
    val state: StateFlow<GameLoadState> = _state.asStateFlow()

    init {
        load()
    }

    /** Tries again after a failure. */
    fun retry() {
        if (_state.value is GameLoadState.Failed) load()
    }

    private fun load() {
        _state.value = GameLoadState.Loading
        viewModelScope.launch {
            _state.value =
                try {
                    source.load(levelId)?.let { GameLoadState.Ready(it) } ?: GameLoadState.Failed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    GameLoadState.Failed
                }
        }
    }

    companion object {
        fun factory(
            levelId: String,
            source: LevelSource,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { GameLoadViewModel(levelId, source) }
            }
    }
}
