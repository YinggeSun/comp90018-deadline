package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreLeaderboardDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProgressDataSource
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.repository.LeaderboardRepositoryImpl
import com.comp90018.deadline.data.repository.ProgressRepositoryImpl
import com.comp90018.deadline.data.sync.ProgressSyncManager
import com.comp90018.deadline.data.sync.SyncResult
import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.progress.CompletionResult
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Firebase SDK, emulator rules and disk-backed progress; missing emulators fail the suite. */
@RunWith(AndroidJUnit4::class)
class OfflineFirebaseIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val apps = mutableListOf<FirebaseApp>()
    private val file = File(context.cacheDir, "offline-${UUID.randomUUID()}.preferences_pb")
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: AuthRepositoryImpl

    private fun newApp() = FirebaseEmulator.newApp(context).also { apps += it }

    @Before
    fun setUp() {
        assertTrue("Required Firebase emulators unavailable", FirebaseEmulator.isRunning())
        FirebaseEmulator.clearFirestore()
        val app = newApp()
        db = FirebaseFirestore.getInstance(app)
        auth = AuthRepositoryImpl(FirebaseAuthDataSource(FirebaseAuth.getInstance(app)), scope)
    }

    @After
    fun close() =
        runBlocking {
            if (::db.isInitialized) db.enableNetwork().await()
            scope.coroutineContext.job.cancelAndJoin()
            diskScope.coroutineContext.job.cancelAndJoin()
            apps.forEach { it.delete() }
            file.delete()
            Unit
        }

    private fun local() = ProgressRepositoryImpl(ProgressDataSource(DeadlineDataStore.create(diskScope) { file }))

    private fun leaderboard() = LeaderboardRepositoryImpl(FirestoreLeaderboardDataSource(db), auth)

    @Test
    fun unavailableAuthServiceCanRetryWithoutReplacingTheRecoveredIdentity() =
        runBlocking {
            withTimeout(60_000) {
                val firebaseAuth = FirebaseAuth.getInstance(db.app)
                val recovery = AuthRepositoryImpl(FirebaseAuthDataSource(firebaseAuth), scope, timeoutMillis = 1_000)
                // Refused local port models an Auth outage without disrupting other Firebase clients.
                firebaseAuth.useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT + 1)
                assertTrue(recovery.ensureSignedIn().isFailure)
                assertEquals(null, firebaseAuth.currentUser)
                val ranking = LeaderboardRepositoryImpl(FirestoreLeaderboardDataSource(db), recovery)
                assertEquals(LeaderboardState.Offline(emptyList()), ranking.observe("level_1").first { it != LeaderboardState.Loading })
                firebaseAuth.useEmulator(FirebaseEmulator.HOST, FirebaseEmulator.AUTH_PORT)
                var uid: String? = null
                withTimeout(30_000) {
                    while (uid == null) {
                        uid = recovery.ensureSignedIn().getOrNull()
                        if (uid == null) delay(100)
                    }
                }
                assertEquals(uid, recovery.ensureSignedIn().getOrThrow())
                assertEquals(uid, firebaseAuth.currentUser?.uid)
                ranking.observe("level_1").first { it is LeaderboardState.Ranked }
                assertEquals(uid, recovery.account.value.uid)
            }
        }

    @Test
    fun offlineWinSurvivesLocalStoreReopenAndUploadsAfterReconnect() =
        runBlocking {
            withTimeout(60_000) {
                val uid = auth.ensureSignedIn().getOrThrow()
                var local = local()
                val remote = FirestoreProgressDataSource(db)
                assertTrue(ProgressSyncManager(local, remote, auth).sync() is SyncResult.Synced)
                db.disableNetwork().await()

                // Use the real engine and a deterministic board, rather than injecting a win.
                val level = FixedLevels.LEVEL_1
                val engine = DefaultGameEngine(level)
                level.board.tiles.forEach { engine.selectTile(it.id) }
                assertEquals(GameStatus.WON, engine.state.status)
                val result = CompletionResult("level_1", 2, 40_000, System.currentTimeMillis())
                local.recordCompletion(result)
                val saved = local.progress.first()
                assertEquals(40_000L, saved.bestFor("level_1")?.timeMillis)
                assertEquals(SubmitResult.Queued, leaderboard().submit(result, "Offline QA"))
                // disableNetwork controls listeners/writes, not every transaction RPC.
                // Actual device network loss and reconnect sync are checked by OfflineGameAcceptanceTest.
                assertEquals(saved, local.progress.first())

                // Close the writer before opening the same file again: verify disk durability.
                diskScope.coroutineContext.job.cancelAndJoin()
                diskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                local = local()
                assertEquals(saved, local.progress.first())
                assertEquals(uid, auth.ensureSignedIn().getOrThrow())

                db.enableNetwork().await()
                db.waitForPendingWrites().await()
                assertTrue(ProgressSyncManager(local, remote, auth).sync() is SyncResult.Synced)
                val cloud = remote.update(uid) { checkNotNull(it) }
                assertEquals(saved, cloud)
                val entry = FirestoreLeaderboardDataSource(db).entryFor("level_1", uid)
                assertEquals(40_000L, entry?.timeMillis)
                assertEquals(SubmitResult.NotFaster, leaderboard().submit(result, "Offline QA"))
                assertEquals(saved, local.progress.first())
            }
        }

    @Test
    fun cachedLeaderboardAndRefusedWriteRecoverWithoutLosingTheRanking() =
        runBlocking {
            withTimeout(45_000) {
                val uid = auth.ensureSignedIn().getOrThrow()
                val repository = leaderboard()
                val result = CompletionResult("level_1", 2, 50_000, System.currentTimeMillis())
                assertEquals(SubmitResult.Failed(LeaderboardFailure.REJECTED), repository.submit(result, ""))
                assertEquals(SubmitResult.Submitted, repository.submit(result, "Retry QA"))
                val states = repository.observe("level_1").stateIn(scope, SharingStarted.Eagerly, LeaderboardState.Loading)
                withTimeout(10_000) {
                    states.first { it is LeaderboardState.Ranked && it.entries.any { entry -> entry.userId == uid } }
                }
                db.disableNetwork().await()
                withTimeout(10_000) {
                    states.first { it is LeaderboardState.Offline && it.cachedEntries.any { entry -> entry.userId == uid } }
                }
                val faster = result.copy(timeMillis = 40_000)
                assertEquals(SubmitResult.Queued, repository.submit(faster, "Retry QA"))
                db.enableNetwork().await()
                db.waitForPendingWrites().await()
                withTimeout(10_000) {
                    states.first {
                        it is LeaderboardState.Ranked && it.entries.any { entry -> entry.userId == uid && entry.timeMillis == 40_000L }
                    }
                }
                Unit
            }
        }

    @Test
    fun rejectedLoginKeepsGuestIdentityAndValidRetryReachesTheRegisteredAccount() =
        runBlocking {
            withTimeout(30_000) {
                val fixture = FirebaseAuth.getInstance(newApp())
                val email = "offline-${UUID.randomUUID()}@example.test"
                val password = "emulator-password"
                val registeredUid = fixture.createUserWithEmailAndPassword(email, password).await().user!!.uid
                val guestUid = auth.ensureSignedIn().getOrThrow()
                assertTrue(auth.login(email, "incorrect-password").isFailure)
                assertEquals(guestUid, auth.ensureSignedIn().getOrThrow())
                assertEquals(guestUid, auth.account.value.uid)
                assertTrue(auth.login(email, password).isSuccess)
                assertEquals(registeredUid, auth.account.value.uid)
                assertTrue(auth.account.value.registered)
                assertTrue(auth.account.value.storageReady)
            }
        }
}
