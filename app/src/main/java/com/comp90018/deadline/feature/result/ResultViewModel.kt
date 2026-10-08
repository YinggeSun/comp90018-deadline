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
 * [previousBestMillis] is the level's Personal Best from before this run.
 * Comparing against it, rather than reading saved progress here, keeps the
 * screen correct whether or not the background save of this run has
 * finished: a win sets a new best when there was none or it is faster, and
 * the best shown is the faster of the two.
 */
class ResultViewModel(
    levelId: String,
    won: Boolean,
    elapsedMillis: Long,
    previousBestMillis: Long?,
    levels: List<Level> = FixedLevels.ALL_LEVELS,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            run {
                val index = levels.indexOfFirst { it.id == levelId }
                val isNewBest = won && (previousBestMillis == null || elapsedMillis < previousBestMillis)
                ResultUiState(
                    levelId = levelId,
                    levelName = levels.getOrNull(index)?.name.orEmpty(),
                    won = won,
                    elapsedMillis = elapsedMillis,
                    bestTimeMillis = if (isNewBest) elapsedMillis else previousBestMillis,
                    isNewBest = isNewBest,
                    nextLevelId = if (won && index >= 0) levels.getOrNull(index + 1)?.id else null,
                )
            },
        )
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    companion object {
        fun factory(
            levelId: String,
            won: Boolean,
            elapsedMillis: Long,
            previousBestMillis: Long?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ResultViewModel(levelId, won, elapsedMillis, previousBestMillis) }
            }
    }
}
