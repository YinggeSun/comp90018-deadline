package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.fake.FakeAuthRepository
import com.comp90018.deadline.data.remote.firebase.LeaderboardPage
import com.comp90018.deadline.data.remote.firebase.LeaderboardStore
import com.comp90018.deadline.data.remote.firebase.LeaderboardStoreException
import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LeaderboardRepositoryImplTest {
    /** In-memory store with switches for the failure modes Firestore can produce. */
    private class FakeStore : LeaderboardStore {
        val stored = MutableStateFlow<Map<Pair<String, String>, LeaderboardEntry>>(emptyMap())
        var fromCache = false
        var observeFailure: LeaderboardFailure? = null
        var writeFailure: LeaderboardFailure? = null
        var readHangs = false
        var writeHangs = false
        val writes = mutableListOf<Pair<LeaderboardEntry, Int>>()

        override fun observeTop(
            levelId: String,
            limit: Int,
        ): Flow<LeaderboardPage> {
            observeFailure?.let { reason -> return flow { throw LeaderboardStoreException(reason) } }
            return stored.map { all ->
                LeaderboardPage(
                    entries = all.values.filter { it.levelId == levelId }.sortedBy { it.timeMillis }.take(limit),
                    isFromCache = fromCache,
                )
            }
        }

        override suspend fun entryFor(
            levelId: String,
            userId: String,
        ): LeaderboardEntry? {
            if (readHangs) awaitCancellation()
            return stored.value[levelId to userId]
        }

        override suspend fun write(
            entry: LeaderboardEntry,
            week: Int,
        ) {
            writes += entry to week
            writeFailure?.let { throw LeaderboardStoreException(it) }
            if (writeHangs) awaitCancellation()
            stored.value = stored.value + ((entry.levelId to entry.userId) to entry)
        }
    }

    private val store = FakeStore()
    private val auth = FakeAuthRepository(userIdOnSignIn = "me")
    private val repository = LeaderboardRepositoryImpl(store, auth)

    private fun result(time: Long) = CompletionResult("level_1", 2, time, 100)

    private fun entry(
        user: String,
        time: Long,
    ) = LeaderboardEntry(user, user, "level_1", time, 0)

    @Test
    fun firstSubmissionIsStoredWithNicknameAndWeek() =
        runTest {
            assertEquals(SubmitResult.Submitted, repository.submit(result(30_000), "Lav"))

            assertEquals(listOf(LeaderboardEntry("me", "Lav", "level_1", 30_000, 100) to 2), store.writes)
        }

    @Test
    fun slowerOrEqualTimeIsNotWritten() =
        runTest {
            repository.submit(result(30_000), "Lav")

            assertEquals(SubmitResult.NotFaster, repository.submit(result(45_000), "Lav"))
            assertEquals(SubmitResult.NotFaster, repository.submit(result(30_000), "Lav"))
            assertEquals(1, store.writes.size)
        }

    @Test
    fun fasterTimeReplacesEntry() =
        runTest {
            repository.submit(result(30_000), "Lav")

            assertEquals(SubmitResult.Submitted, repository.submit(result(20_000), "Lav"))
            assertEquals(20_000L, store.stored.value["level_1" to "me"]?.timeMillis)
        }

    @Test
    fun unacknowledgedWriteIsReportedAsQueued() =
        runTest {
            store.writeHangs = true

            assertEquals(SubmitResult.Queued, repository.submit(result(30_000), "Lav"))
        }

    @Test
    fun unreadableExistingEntryStillAttemptsTheWrite() =
        runTest {
            store.readHangs = true

            assertEquals(SubmitResult.Submitted, repository.submit(result(30_000), "Lav"))
        }

    @Test
    fun serverRefusalIsTyped() =
        runTest {
            store.writeFailure = LeaderboardFailure.REJECTED

            assertEquals(SubmitResult.Failed(LeaderboardFailure.REJECTED), repository.submit(result(30_000), "Lav"))
        }

    @Test
    fun noIdentityMeansNotSignedIn() =
        runTest {
            val repository = LeaderboardRepositoryImpl(store, FakeAuthRepository(userIdOnSignIn = null))

            assertEquals(SubmitResult.Failed(LeaderboardFailure.NOT_SIGNED_IN), repository.submit(result(30_000), "Lav"))
            assertEquals(0, store.writes.size)
        }

    @Test
    fun rankingIsFastestFirstAfterLoading() =
        runTest {
            store.stored.value = mapOf(("level_1" to "a") to entry("a", 30_000), ("level_1" to "b") to entry("b", 20_000))

            val states = repository.observe("level_1").take(2).toList()

            assertEquals(LeaderboardState.Loading, states[0])
            assertEquals(listOf("b", "a"), (states[1] as LeaderboardState.Ranked).entries.map { it.userId })
        }

    @Test
    fun cachedRankingIsReportedAsOffline() =
        runTest {
            store.stored.value = mapOf(("level_1" to "a") to entry("a", 30_000))
            store.fromCache = true

            val state = repository.observe("level_1").take(2).toList().last()

            assertEquals(LeaderboardState.Offline(listOf(entry("a", 30_000))), state)
        }

    @Test
    fun rankingUpdatesWhenAnotherPlayerPosts() =
        runTest {
            val states = mutableListOf<LeaderboardState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.observe("level_1").collect { states += it }
            }

            store.stored.value = mapOf(("level_1" to "other") to entry("other", 25_000))
            testScheduler.runCurrent()

            assertEquals(listOf("other"), (states.last() as LeaderboardState.Ranked).entries.map { it.userId })
        }

    @Test
    fun storeFailureBecomesTypedError() =
        runTest {
            store.observeFailure = LeaderboardFailure.REJECTED

            val state = repository.observe("level_1").take(2).toList().last()

            assertEquals(LeaderboardState.Error(LeaderboardFailure.REJECTED), state)
        }

    @Test
    fun signInNetworkFailureIsOfflineButOtherFailureIsNotSignedIn() =
        runTest {
            fun failingAuth(error: Throwable) =
                object : AuthRepository {
                    override val userId = MutableStateFlow<String?>(null)

                    override suspend fun ensureSignedIn() = Result.failure<String>(error)
                }

            val offline = LeaderboardRepositoryImpl(store, failingAuth(IOException("offline")))
            val refused = LeaderboardRepositoryImpl(store, failingAuth(IllegalStateException("disabled")))

            assertEquals(LeaderboardState.Offline(emptyList()), offline.observe("level_1").take(2).toList().last())
            assertEquals(LeaderboardState.Error(LeaderboardFailure.NOT_SIGNED_IN), refused.observe("level_1").take(2).toList().last())
        }
}
