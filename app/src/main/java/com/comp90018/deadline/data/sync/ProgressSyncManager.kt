package com.comp90018.deadline.data.sync

import com.comp90018.deadline.data.remote.firebase.RemoteProgressException
import com.comp90018.deadline.data.remote.firebase.RemoteProgressStore
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.AuthRepository
import com.comp90018.deadline.domain.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** What one sync achieved. Nothing here is an error the player needs to see. */
sealed interface SyncResult {
    /** Local and cloud progress now both hold [progress]. */
    data class Synced(
        val progress: PlayerProgress,
    ) : SyncResult

    /** No connection; local progress is untouched and the next sync will try again. */
    data object Offline : SyncResult

    /** No anonymous identity yet, so there is no cloud copy to sync with. */
    data object NotSignedIn : SyncResult

    /** The server refused the write (for example invalid data); local progress is untouched. */
    data object Refused : SyncResult

    data object Failed : SyncResult
}

/**
 * Keeps local progress and its cloud copy in step. A sync merges local progress into the cloud
 * copy in one server transaction, then merges the result back into local progress, both with
 * [ConflictResolver], so neither side can lose a completed level, an unlocked week or a faster
 * Personal Best. Syncs run one at a time; call [sync] at startup, after a win and when the
 * device reconnects.
 */
class ProgressSyncManager(
    private val local: ProgressRepository,
    private val remote: RemoteProgressStore,
    private val auth: AuthRepository,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val syncLock = Mutex()

    suspend fun sync(): SyncResult =
        syncLock.withLock {
            val userId = auth.ensureSignedIn().getOrElse { return@withLock SyncResult.NotSignedIn }
            val localProgress = local.progress.first()
            val synced =
                try {
                    withTimeout(timeoutMillis) {
                        remote.update(userId) { cloud -> ConflictResolver.merge(localProgress, cloud ?: PlayerProgress()) }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    return@withLock SyncResult.Offline
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: RemoteProgressException) {
                    return@withLock when {
                        error.isRefused -> SyncResult.Refused
                        error.isOffline -> SyncResult.Offline
                        else -> SyncResult.Failed
                    }
                } catch (error: Exception) {
                    return@withLock SyncResult.Failed
                }
            // Local progress may have improved during the sync; merging keeps both. Saving can
            // still fail (for example a full disk); that is reported, never thrown, because a
            // sync runs in the background where an exception would crash the app.
            try {
                SyncResult.Synced(local.mergeIn(synced))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SyncResult.Failed
            }
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
