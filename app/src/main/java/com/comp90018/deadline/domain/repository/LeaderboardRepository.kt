package com.comp90018.deadline.domain.repository

import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.progress.CompletionResult
import kotlinx.coroutines.flow.Flow

/** Per-level online leaderboard. */
interface LeaderboardRepository {
    /** Live ranking for [levelId], fastest first; updates while collected. */
    fun observe(
        levelId: String,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<LeaderboardState>

    /** Publishes [result] under [nickname] if it beats this player's stored entry. Never throws. */
    suspend fun submit(
        result: CompletionResult,
        nickname: String,
    ): SubmitResult

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
