package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.remote.firebase.LeaderboardStore
import com.comp90018.deadline.data.remote.firebase.LeaderboardStoreException
import com.comp90018.deadline.data.remote.isNetworkError
import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.repository.AuthRepository
import com.comp90018.deadline.domain.repository.LeaderboardRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Online leaderboard on top of a [LeaderboardStore]. Nothing here throws: a lost connection
 * becomes [LeaderboardState.Offline] or [SubmitResult.Queued], and refusals become typed
 * [LeaderboardFailure]s, so the leaderboard can cost a player a placing but never a level.
 *
 * A submission waits up to [writeAckTimeoutMillis] for the server; after that the write
 * stays queued on the device and is uploaded when it reconnects.
 */
class LeaderboardRepositoryImpl(
    private val store: LeaderboardStore,
    private val auth: AuthRepository,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    private val writeAckTimeoutMillis: Long = DEFAULT_WRITE_ACK_TIMEOUT_MILLIS,
) : LeaderboardRepository {
    override fun observe(
        levelId: String,
        limit: Int,
    ): Flow<LeaderboardState> =
        flow {
            emit(LeaderboardState.Loading)
            val signInFailure = auth.ensureSignedIn().exceptionOrNull()
            if (signInFailure != null) {
                emit(
                    if (signInFailure.isNetworkError()) {
                        LeaderboardState.Offline(emptyList())
                    } else {
                        LeaderboardState.Error(LeaderboardFailure.NOT_SIGNED_IN)
                    },
                )
                return@flow
            }
            emitAll(
                store.observeTop(levelId, limit)
                    .map { page ->
                        if (page.isFromCache) {
                            LeaderboardState.Offline(page.entries)
                        } else {
                            LeaderboardState.Ranked(page.entries)
                        }
                    }.catch { error ->
                        emit(LeaderboardState.Error((error as? LeaderboardStoreException)?.reason ?: LeaderboardFailure.UNKNOWN))
                    },
            )
        }

    override suspend fun submit(
        result: CompletionResult,
        nickname: String,
    ): SubmitResult {
        val userId =
            auth.ensureSignedIn().getOrElse {
                return SubmitResult.Failed(LeaderboardFailure.NOT_SIGNED_IN)
            }
        // If the existing entry cannot be read in time or is malformed, write anyway: the
        // server only accepts an update that is faster than the stored time.
        val existing =
            try {
                withTimeoutOrNull(readTimeoutMillis) { store.entryFor(result.levelId, userId) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            }
        if (existing != null && existing.timeMillis <= result.timeMillis) return SubmitResult.NotFaster

        val entry = LeaderboardEntry(userId, nickname, result.levelId, result.timeMillis, result.completedAtMillis)
        return try {
            withTimeout(writeAckTimeoutMillis) { store.write(entry, result.week) }
            SubmitResult.Submitted
        } catch (timeout: TimeoutCancellationException) {
            SubmitResult.Queued
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: LeaderboardStoreException) {
            SubmitResult.Failed(error.reason)
        } catch (error: Exception) {
            SubmitResult.Failed(LeaderboardFailure.UNKNOWN)
        }
    }

    private companion object {
        const val DEFAULT_READ_TIMEOUT_MILLIS = 3_000L
        const val DEFAULT_WRITE_ACK_TIMEOUT_MILLIS = 5_000L
    }
}
