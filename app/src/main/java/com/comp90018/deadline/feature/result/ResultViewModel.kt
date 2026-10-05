package com.comp90018.deadline.feature.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Builds the Result screen from the finished game's summary.
 *
 * Personal Bests are not persisted yet (#36), so by default there is no
 * previous best; [bestTimeMillis] is the seam for the progress repository.
 * Recording a new best is also left to #36.
 */
class ResultViewModel(
    levelId: String,
    won: Boolean,
    elapsedMillis: Long,
    levels: List<Level> = FixedLevels.ALL_LEVELS,
    bestTimeMillis: (levelId: String) -> Long? = { null }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        run {
            val index = levels.indexOfFirst { it.id == levelId }
            val best = bestTimeMillis(levelId)
            ResultUiState(
                levelId = levelId,
                levelName = levels.getOrNull(index)?.name.orEmpty(),
                won = won,
                elapsedMillis = elapsedMillis,
                bestTimeMillis = best,
                isNewBest = won && best != null && elapsedMillis < best,
                nextLevelId = if (won && index >= 0) levels.getOrNull(index + 1)?.id else null
            )
        }
    )
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    companion object {
        fun factory(levelId: String, won: Boolean, elapsedMillis: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ResultViewModel(levelId, won, elapsedMillis) }
            }
    }
}
