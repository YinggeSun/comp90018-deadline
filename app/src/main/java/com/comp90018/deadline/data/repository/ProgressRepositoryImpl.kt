package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.domain.progress.CompletionOutcome
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow

/** Local-only for now; #39 adds the Firestore mirror behind this same interface. */
class ProgressRepositoryImpl(private val local: ProgressDataSource) : ProgressRepository {
    override val progress: Flow<PlayerProgress> = local.progress

    override suspend fun recordCompletion(result: CompletionResult): CompletionOutcome = local.recordCompletion(result)

    override suspend fun mergeIn(other: PlayerProgress): PlayerProgress = local.mergeIn(other)
}
