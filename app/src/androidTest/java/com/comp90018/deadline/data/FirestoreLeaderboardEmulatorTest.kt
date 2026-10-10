package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreLeaderboardDataSource
import com.comp90018.deadline.data.remote.firebase.LeaderboardStoreException
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.repository.LeaderboardRepositoryImpl
import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.progress.CompletionResult
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real Firestore leaderboard code and `firestore.rules` against the local Firebase
 * Emulator Suite (see [FirebaseEmulator]); each player is a separate FirebaseApp. The database
 * is cleared before each test. Skipped when the emulators are not running.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreLeaderboardEmulatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apps = mutableListOf<FirebaseApp>()

    private val levelId = "level_1"

    private inner class Player {
        val app = FirebaseEmulator.newApp(context).also { apps += it }
        val auth = FirebaseAuth.getInstance(app)
        val store = FirestoreLeaderboardDataSource(FirebaseFirestore.getInstance(app))
        val repository = LeaderboardRepositoryImpl(store, AuthRepositoryImpl(FirebaseAuthDataSource(auth), scope))

        suspend fun signIn(): String = checkNotNull(auth.signInAnonymously().await().user).uid
    }

    private fun result(timeMillis: Long) = CompletionResult(levelId, 2, timeMillis, 1)

    private suspend fun Player.rankingWhere(condition: (List<LeaderboardEntry>) -> Boolean) =
        (repository.observe(levelId).first { it is LeaderboardState.Ranked && condition(it.entries) } as LeaderboardState.Ranked).entries

    @Before
    fun requireEmulators() {
        assumeTrue("Firebase emulators are not running on the host", FirebaseEmulator.isRunning())
        FirebaseEmulator.clearFirestore()
    }

    @After
    fun tearDown() {
        scope.cancel()
        apps.forEach { it.delete() }
    }

    @Test
    fun rankingIsFastestFirstAcrossPlayers() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val slow = Player()
                val fast = Player()

                assertEquals(SubmitResult.Submitted, slow.repository.submit(result(30_000), "Slow"))
                assertEquals(SubmitResult.Submitted, fast.repository.submit(result(20_000), "Fast"))

                assertEquals(listOf("Fast", "Slow"), slow.rankingWhere { it.size == 2 }.map { it.nickname })
            }
        }

    @Test
    fun onlyAFasterTimeReplacesThePlayersEntry() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val player = Player()

                assertEquals(SubmitResult.Submitted, player.repository.submit(result(30_000), "Lav"))
                assertEquals(SubmitResult.NotFaster, player.repository.submit(result(45_000), "Lav"))
                assertEquals(SubmitResult.Submitted, player.repository.submit(result(20_000), "Lav"))

                val ranking = player.rankingWhere { entries -> entries.any { it.timeMillis == 20_000L } }
                assertEquals(listOf(20_000L), ranking.map { it.timeMillis })
            }
        }

    @Test
    fun rankingUpdatesLiveWhenAnotherPlayerPosts() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val watcher = Player()
                val other = Player()
                watcher.repository.submit(result(30_000), "Watcher")

                // One listener, opened before the other player posts, must receive the new entry.
                val latest = MutableStateFlow<LeaderboardState>(LeaderboardState.Loading)
                val listening = launch { watcher.repository.observe(levelId).collect { latest.value = it } }
                latest.first { it is LeaderboardState.Ranked && it.entries.size == 1 }

                other.repository.submit(result(10_000), "Other")

                val updated = latest.first { it is LeaderboardState.Ranked && it.entries.size == 2 } as LeaderboardState.Ranked
                assertEquals(listOf("Other", "Watcher"), updated.entries.map { it.nickname })
                listening.cancel()
            }
        }

    @Test
    fun rulesRejectWritingAnotherPlayersEntry() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val player = Player()
                player.signIn()

                val error =
                    runCatching { player.store.write(LeaderboardEntry("someone-else", "Fake", levelId, 20_000, 0), 2) }
                        .exceptionOrNull()

                assertTrue(error is LeaderboardStoreException)
                assertEquals(LeaderboardFailure.REJECTED, (error as LeaderboardStoreException).reason)
            }
        }

    @Test
    fun rulesRejectASlowerUpdateEvenWithoutTheClientCheck() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val player = Player()
                val uid = player.signIn()
                player.store.write(LeaderboardEntry(uid, "Lav", levelId, 20_000, 0), 2)

                val error = runCatching { player.store.write(LeaderboardEntry(uid, "Lav", levelId, 40_000, 0), 2) }.exceptionOrNull()

                assertEquals(LeaderboardFailure.REJECTED, (error as? LeaderboardStoreException)?.reason)
            }
        }

    @Test
    fun rulesRejectReadingWithoutSignIn() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val stranger = Player()

                val error = runCatching { stranger.store.observeTop(levelId, 10).first() }.exceptionOrNull()

                assertEquals(LeaderboardFailure.REJECTED, (error as? LeaderboardStoreException)?.reason)
            }
        }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
