package com.comp90018.deadline.data.fake

import com.comp90018.deadline.domain.progress.CompletionOutcome
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [ProgressRepository] for ViewModel and integration tests. */
class FakeProgressRepository(initial: PlayerProgress = PlayerProgress()) : ProgressRepository {
    private val state = MutableStateFlow(initial)
    override val progress: StateFlow<PlayerProgress> = state

    val recorded = mutableListOf<CompletionResult>()

    override suspend fun recordCompletion(result: CompletionResult): CompletionOutcome {
        recorded += result
        val (updated, outcome) = state.value.withCompletion(result)
        state.value = updated
        return outcome
    }
}
