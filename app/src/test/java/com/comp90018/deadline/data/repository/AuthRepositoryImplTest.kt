package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.remote.firebase.AnonymousAuthClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImplTest {
    /** Scriptable stand-in for Firebase Auth. */
    private class FakeClient(signedInAs: String? = null) : AnonymousAuthClient {
        private val user = MutableStateFlow(signedInAs)
        var signInCalls = 0
        var signIn: suspend () -> String = { "new-user" }

        override val currentUserId: String? get() = user.value

        override fun userIdChanges(): Flow<String?> = user

        override suspend fun signInAnonymously(): String {
            signInCalls++
            return signIn().also { user.value = it }
        }
    }

    @Test
    fun reusesExistingUserWithoutSigningInAgain() =
        runTest {
            val client = FakeClient(signedInAs = "existing")

            assertEquals("existing", AuthRepositoryImpl(client, backgroundScope).ensureSignedIn().getOrThrow())
            assertEquals(0, client.signInCalls)
        }

    @Test
    fun signsInAnonymouslyWhenSignedOut() =
        runTest {
            val client = FakeClient()
            val repository = AuthRepositoryImpl(client, backgroundScope)

            assertEquals("new-user", repository.ensureSignedIn().getOrThrow())
            assertEquals("new-user", client.currentUserId)
        }

    @Test
    fun offlineFailureIsReturnedNotThrown() =
        runTest {
            val client = FakeClient().apply { signIn = { throw IOException("offline") } }

            val result = AuthRepositoryImpl(client, backgroundScope).ensureSignedIn()

            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun hangingSignInTimesOutAsFailure() =
        runTest {
            val client = FakeClient().apply { signIn = { awaitCancellation() } }

            val result = AuthRepositoryImpl(client, backgroundScope, timeoutMillis = 5_000).ensureSignedIn()

            assertTrue(result.isFailure)
        }

    @Test
    fun retrySucceedsAfterEarlierFailure() =
        runTest {
            val client = FakeClient().apply { signIn = { throw IOException("offline") } }
            val repository = AuthRepositoryImpl(client, backgroundScope)
            repository.ensureSignedIn()

            client.signIn = { "later-user" }

            assertEquals("later-user", repository.ensureSignedIn().getOrThrow())
        }

    @Test
    fun concurrentCallersShareOneSignIn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val client =
                FakeClient().apply {
                    signIn = {
                        gate.await()
                        "shared-user"
                    }
                }
            val repository = AuthRepositoryImpl(client, backgroundScope)

            val callers = List(5) { async { repository.ensureSignedIn() } }
            runCurrent() // every caller is now waiting, before the first sign-in finishes
            gate.complete(Unit)

            assertEquals(List(5) { "shared-user" }, callers.awaitAll().map { it.getOrThrow() })
            assertEquals(1, client.signInCalls)
        }

    @Test
    fun concurrentCallersShareOneFailedAttemptInsteadOfRetryingInTurn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val client =
                FakeClient().apply {
                    signIn = {
                        gate.await()
                        throw IOException("offline")
                    }
                }
            val repository = AuthRepositoryImpl(client, backgroundScope)

            val callers = List(5) { async { repository.ensureSignedIn() } }
            runCurrent()
            gate.complete(Unit)

            assertTrue(callers.awaitAll().all { it.exceptionOrNull() is IOException })
            assertEquals(1, client.signInCalls)
        }

    @Test
    fun cancelledCallerDoesNotCancelSharedAttempt() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val client =
                FakeClient().apply {
                    signIn = {
                        gate.await()
                        "shared-user"
                    }
                }
            val repository = AuthRepositoryImpl(client, backgroundScope)

            val first = async { repository.ensureSignedIn() }
            val second = async { repository.ensureSignedIn() }
            runCurrent()
            first.cancel()
            gate.complete(Unit)

            assertEquals("shared-user", second.await().getOrThrow())
        }

    @Test
    fun retryAfterTimeoutReusesTheStillRunningSignIn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val client =
                FakeClient().apply {
                    signIn = {
                        gate.await()
                        "late-user"
                    }
                }
            val repository = AuthRepositoryImpl(client, backgroundScope, timeoutMillis = 5_000)

            assertTrue(repository.ensureSignedIn().isFailure) // times out; Firebase is still working
            assertTrue(repository.ensureSignedIn().isFailure) // retry must wait on the same operation

            assertEquals(1, client.signInCalls)
        }

    @Test
    fun lateCompletionAfterTimeoutIsKeptWithoutASecondSignIn() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val client =
                FakeClient().apply {
                    signIn = {
                        gate.await()
                        "late-user"
                    }
                }
            val repository = AuthRepositoryImpl(client, backgroundScope, timeoutMillis = 5_000)
            assertTrue(repository.ensureSignedIn().isFailure)

            gate.complete(Unit)
            runCurrent()

            assertEquals("late-user", repository.ensureSignedIn().getOrThrow())
            assertEquals(1, client.signInCalls)
        }

    @Test
    fun aRetryWaitsForTheRunningSignInAndGetsItsResult() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val client =
                FakeClient().apply {
                    signIn = {
                        gate.await()
                        "late-user"
                    }
                }
            val repository = AuthRepositoryImpl(client, backgroundScope, timeoutMillis = 5_000)
            assertTrue(repository.ensureSignedIn().isFailure)

            val retry = async { repository.ensureSignedIn() }
            runCurrent()
            gate.complete(Unit)

            assertEquals("late-user", retry.await().getOrThrow())
            assertEquals(1, client.signInCalls)
        }

    @Test
    fun aFailedSignInIsRetriedWithANewOperation() =
        runTest {
            val client = FakeClient().apply { signIn = { throw IOException("offline") } }
            val repository = AuthRepositoryImpl(client, backgroundScope)
            assertTrue(repository.ensureSignedIn().isFailure)

            client.signIn = { "online-user" }

            assertEquals("online-user", repository.ensureSignedIn().getOrThrow())
            assertEquals(2, client.signInCalls)
        }

    @Test
    fun cancellingTheCallerIsNotReportedAsAFailure() =
        runTest {
            val client = FakeClient().apply { signIn = { awaitCancellation() } }
            val repository = AuthRepositoryImpl(client, backgroundScope)

            val caller = async { repository.ensureSignedIn() }
            runCurrent()
            caller.cancel()

            assertTrue(runCatching { caller.await() }.exceptionOrNull() is CancellationException)
        }
}
