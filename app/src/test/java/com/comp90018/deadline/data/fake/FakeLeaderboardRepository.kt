package com.comp90018.deadline.data.fake

import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * In-memory [LeaderboardRepository] holding one entry per user per level, keeping the
 * faster time. Set [offline] to simulate a lost connection (writes are queued and applied
 * on reconnect), or [failure] to make every read and write fail with that reason.
 */
class FakeLeaderboardRepository(
    private val userId: String = "test-user"
) : LeaderboardRepository {
    private val entries = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    private val isOffline = MutableStateFlow(false)

    private val queued = mutableListOf<Pair<CompletionResult, String>>()

    var offline: Boolean
        get() = isOffline.value
        set(value) {
            isOffline.value = value
            if (!value) queued.toList().also { queued.clear() }.forEach { (r, n) -> write(r, n) }
        }

    var failure: LeaderboardFailure? = null

    override fun observe(levelId: String, limit: Int): Flow<LeaderboardState> =
        combine(entries, isOffline) { all, offline ->
            failure?.let { return@combine LeaderboardState.Error(it) }
            val ranked = all.filter { it.levelId == levelId }.sortedBy { it.timeMillis }.take(limit)
            if (offline) LeaderboardState.Offline(ranked) else LeaderboardState.Ranked(ranked)
        }

    override suspend fun submit(result: CompletionResult, nickname: String): SubmitResult {
        failure?.let { return SubmitResult.Failed(it) }
        if (offline) {
            queued += result to nickname
            return SubmitResult.Queued
        }
        return if (write(result, nickname)) SubmitResult.Submitted else SubmitResult.NotFaster
    }

    private fun write(result: CompletionResult, nickname: String): Boolean {
        val existing = entries.value.find { it.userId == userId && it.levelId == result.levelId }
        if (existing != null && result.timeMillis >= existing.timeMillis) return false
        entries.value = entries.value - listOfNotNull(existing).toSet() + LeaderboardEntry(
            userId, nickname, result.levelId, result.timeMillis, result.completedAtMillis
        )
        return true
    }

    /** Adds another player's entry directly, as if posted from a different device. */
    fun seed(entry: LeaderboardEntry) {
        entries.value = entries.value + entry
    }
}
