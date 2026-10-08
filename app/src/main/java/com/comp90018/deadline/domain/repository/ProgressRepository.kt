package com.comp90018.deadline.domain.repository

import com.comp90018.deadline.domain.progress.CompletionOutcome
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.Flow

/** Unlocked Weeks, completed levels and Personal Bests, local-first. */
interface ProgressRepository {
    /** Current progress, re-emitted on every change; starts at a fresh [PlayerProgress]. */
    val progress: Flow<PlayerProgress>

    /** Applies [PlayerProgress.withCompletion] and persists the result atomically. */
    suspend fun recordCompletion(result: CompletionResult): CompletionOutcome
}
