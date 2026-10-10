package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.remote.firebase.AnonymousAuthClient
import com.comp90018.deadline.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Anonymous identity with no sign-up step. An existing user is reused. Only one Firebase
 * sign-in runs at a time: it is started in [scope] and kept until Firebase finishes it, and
 * every caller that arrives meanwhile waits on that same operation. [timeoutMillis] limits
 * each caller's wait, not the operation, so a retry after a timeout joins the sign-in that is
 * still running instead of starting a second one (which could replace the identity when the
 * first completes late). Failures (offline, timeout, server error) come back as
 * [Result.failure]; cancelling a caller stops only that caller's wait and is rethrown to it.
 */
class AuthRepositoryImpl(
    private val client: AnonymousAuthClient,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : AuthRepository {
    private val operationLock = Mutex()
    private var currentOperation: Deferred<Result<String>>? = null

    override val userId: Flow<String?> = client.userIdChanges()

    override suspend fun ensureSignedIn(): Result<String> {
        client.currentUserId?.let { return Result.success(it) }
        val operation =
            operationLock.withLock {
                currentOperation?.takeIf { it.isActive }
                    ?: scope.async { signInOnce() }.also { currentOperation = it }
            }
        return try {
            withTimeout(timeoutMillis) { operation.await() }
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(timeout)
        }
    }

    /**
     * The shared operation. It keeps its failure as a result instead of throwing, so a failed
     * sign-in reaches every waiting caller and never fails [scope] or the work running in it.
     */
    private suspend fun signInOnce(): Result<String> =
        try {
            Result.success(client.signInAnonymously())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
