package com.comp90018.deadline.feature.levelselect

import androidx.lifecycle.ViewModel
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lists the playable levels with their lock state and Personal Best.
 *
 * Progress is not persisted yet (#35 / #36), so by default every level is
 * unlocked and has no best time. [isUnlocked] and [bestTimeSeconds] are the
 * seams for the progress repository once it exists.
 */
class LevelSelectViewModel(
    levels: List<Level> = FixedLevels.ALL_LEVELS,
    isUnlocked: (Level) -> Boolean = { true },
    bestTimeSeconds: (Level) -> Long? = { null }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LevelSelectUiState(
            levels = levels.map { level ->
                LevelItemUiModel(
                    id = level.id,
                    name = level.name,
                    tileCount = level.board.tiles.size,
                    layerCount = level.board.tiles.map { it.position.layer }.distinct().size,
                    isLocked = !isUnlocked(level),
                    bestTimeSeconds = bestTimeSeconds(level)
                )
            }
        )
    )
    val uiState: StateFlow<LevelSelectUiState> = _uiState.asStateFlow()
}
