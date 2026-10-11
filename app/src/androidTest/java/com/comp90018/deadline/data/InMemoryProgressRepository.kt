package com.comp90018.deadline.data

import com.comp90018.deadline.data.sync.ConflictResolver
import com.comp90018.deadline.domain.progress.CompletionOutcome
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A device's local progress, kept in memory, for emulator tests that stand in for devices. */
class InMemoryProgressRepository(
    initial: PlayerProgress = PlayerProgress(),
) : ProgressRepository {
    private val state = MutableStateFlow(initial)
    override val progress: StateFlow<PlayerProgress> = state

    override suspend fun recordCompletion(result: CompletionResult): CompletionOutcome {
        val (updated, outcome) = state.value.withCompletion(result)
        state.value = updated
        return outcome
    }

    override suspend fun mergeIn(other: PlayerProgress) = ConflictResolver.merge(state.value, other).also { state.value = it }
}
