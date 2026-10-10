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
import com.google.firebase.FirebaseOptions
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
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID

/**
 * Runs the real Firestore leaderboard code and `firestore.rules` against the local Firebase
 * Emulator Suite, never the real project: each player is a separate FirebaseApp on the
 * `demo-deadline` project. Skipped when the emulators are not running; start them with
 * `firebase emulators:start --only auth,firestore --project demo-deadline`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreLeaderboardEmulatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apps = mutableListOf<FirebaseApp>()

    /** A fresh level ID per test, so tests never see each other's entries. */
    private val levelId = "test_${UUID.randomUUID()}"

    private inner class Player {
        val app =
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(PROJECT_ID)
                    .setApplicationId("1:1:android:1")
                    .setApiKey("emulator-only")
                    .build(),
                "player-${UUID.randomUUID()}",
            ).also { apps += it }
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        val store = FirestoreLeaderboardDataSource(FirebaseFirestore.getInstance(app).apply { useEmulator(HOST, FIRESTORE_PORT) })
        val repository = LeaderboardRepositoryImpl(store, AuthRepositoryImpl(FirebaseAuthDataSource(auth), scope))

        suspend fun signIn(): String = checkNotNull(auth.signInAnonymously().await().user).uid
    }

    private fun result(timeMillis: Long) = CompletionResult(levelId, 2, timeMillis, 1)

    private suspend fun Player.rankingWhere(condition: (List<LeaderboardEntry>) -> Boolean) =
        (repository.observe(levelId).first { it is LeaderboardState.Ranked && condition(it.entries) } as LeaderboardState.Ranked).entries

    @Before
    fun requireEmulators() {
        assumeTrue("Firebase emulators are not running on the host", reachable(FIRESTORE_PORT) && reachable(AUTH_PORT))
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
    fun malformedEntriesAreSkippedInTheRankingWithoutCrashing() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val player = Player()
                player.repository.submit(result(30_000), "Valid")
                writeAsAdmin("leaderboard/$levelId/entries/broken", malformedEntry(uid = "broken", timeMillis = 20_000))

                val ranking = player.rankingWhere { it.isNotEmpty() }

                assertEquals(listOf("Valid"), ranking.map { it.nickname })
            }
        }

    @Test
    fun submittingOverAMalformedOwnEntryDoesNotThrow() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val player = Player()
                val uid = player.signIn()
                writeAsAdmin("leaderboard/$levelId/entries/$uid", malformedEntry(uid = uid, timeMillis = 50_000))

                assertEquals(SubmitResult.Submitted, player.repository.submit(result(30_000), "Lav"))
                assertEquals(
                    listOf(30_000L),
                    player.rankingWhere {
                            entries ->
                        entries.any { it.timeMillis == 30_000L }
                    }.map { it.timeMillis },
                )
            }
        }

    /** An entry whose submittedAt is text, as an older or hand-edited record could be. */
    private fun malformedEntry(
        uid: String,
        timeMillis: Long,
    ) = """{"fields": {
        "uid": {"stringValue": "$uid"}, "nickname": {"stringValue": "Broken"},
        "timeMillis": {"integerValue": "$timeMillis"}, "week": {"integerValue": "2"},
        "submittedAt": {"stringValue": "yesterday"}}}"""

    /**
     * Writes a document with the emulator's admin access, bypassing the rules, to stand in for
     * records that already exist (for example from before the rules were tightened).
     */
    private fun writeAsAdmin(
        path: String,
        json: String,
    ) {
        val collection = path.substringBeforeLast('/')
        val documentId = path.substringAfterLast('/')
        val url =
            URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents/$collection?documentId=$documentId")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(json.toByteArray()) }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Admin write failed: ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun rulesRejectWritingAnotherPlayersEntry() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val player = Player()
                player.signIn()

                val error =
                    runCatching { player.store.write(LeaderboardEntry("someone-else", "Fake", levelId, 1_000, 0), 2) }
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

    private fun reachable(port: Int) = runCatching { Socket().use { it.connect(InetSocketAddress(HOST, port), 1_000) } }.isSuccess

    private companion object {
        /** The host machine, as seen from the Android emulator. */
        const val HOST = "10.0.2.2"
        const val AUTH_PORT = 9099
        const val FIRESTORE_PORT = 8080
        const val PROJECT_ID = "demo-deadline"
        const val TIMEOUT = 20_000L
    }
}
