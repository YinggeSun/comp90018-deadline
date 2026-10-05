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
 * Anonymous identity with no sign-up step. An existing user is reused, and callers that
 * arrive while a sign-in is running share that attempt and its result, success or failure.
 * Failures (offline, timeout, server error) come back as [Result.failure] so no caller can
 * be blocked or crashed by the network. The attempt runs in [scope], so one caller giving
 * up does not cancel it for the others.
 */
class AuthRepositoryImpl(
    private val client: AnonymousAuthClient,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : AuthRepository {
    private val attemptLock = Mutex()
    private var currentAttempt: Deferred<Result<String>>? = null

    override val userId: Flow<String?> = client.userIdChanges()

    override suspend fun ensureSignedIn(): Result<String> {
        client.currentUserId?.let { return Result.success(it) }
        val attempt = attemptLock.withLock {
            currentAttempt?.takeIf { it.isActive }
                ?: scope.async { signIn() }.also { currentAttempt = it }
        }
        return attempt.await()
    }

    private suspend fun signIn(): Result<String> = try {
        Result.success(withTimeout(timeoutMillis) { client.signInAnonymously() })
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
