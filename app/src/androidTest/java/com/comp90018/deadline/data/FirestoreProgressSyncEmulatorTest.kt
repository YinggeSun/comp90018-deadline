package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProgressDataSource
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.sync.ConflictResolver
import com.comp90018.deadline.data.sync.ProgressSyncManager
import com.comp90018.deadline.data.sync.SyncResult
import com.comp90018.deadline.domain.progress.CompletionOutcome
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.ProgressRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs progress sync against the Firebase Emulator Suite (see [FirebaseEmulator]) with the real
 * `firestore.rules`. Two local copies on one signed-in account stand in for two devices.
 * Skipped when the emulators are not running.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreProgressSyncEmulatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: FirebaseApp
    private lateinit var auth: AuthRepositoryImpl
    private lateinit var remote: FirestoreProgressDataSource

    /** A device's local progress, kept in memory. */
    private class Device(
        initial: PlayerProgress,
    ) : ProgressRepository {
        private val state = MutableStateFlow(initial)
        override val progress: StateFlow<PlayerProgress> = state

        override suspend fun recordCompletion(result: CompletionResult): CompletionOutcome {
            val (updated, outcome) = state.value.withCompletion(result)
            state.value = updated
            return outcome
        }

        override suspend fun mergeIn(other: PlayerProgress) = ConflictResolver.merge(state.value, other).also { state.value = it }
    }

    private fun progress(
        week: Int,
        vararg bests: Pair<String, Long>,
    ) = PlayerProgress(
        completedLevelIds = bests.map { it.first }.toSet(),
        highestUnlockedWeek = week,
        personalBests = bests.associate { (level, time) -> level to PersonalBest(level, time, 1) },
    )

    @Before
    fun setUp() {
        assumeTrue("Firebase emulators are not running on the host", FirebaseEmulator.isRunning())
        FirebaseEmulator.clearFirestore()
        app = FirebaseEmulator.newApp(context)
        auth = AuthRepositoryImpl(FirebaseAuthDataSource(FirebaseAuth.getInstance(app)), scope)
        remote = FirestoreProgressDataSource(FirebaseFirestore.getInstance(app))
    }

    @After
    fun tearDown() {
        scope.cancel()
        if (::app.isInitialized) app.delete()
    }

    @Test
    fun firstSyncStoresLocalProgressInTheCloud() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val phone = Device(progress(3, "level_1" to 40_000))

                assertEquals(SyncResult.Synced(progress(3, "level_1" to 40_000)), ProgressSyncManager(phone, remote, auth).sync())

                val uid = auth.ensureSignedIn().getOrThrow()
                assertEquals(progress(3, "level_1" to 40_000), remote.update(uid) { checkNotNull(it) })
            }
        }

    @Test
    fun twoDevicesOnOneAccountEndUpWithEverything() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val phone = Device(progress(3, "level_1" to 40_000))
                val tablet = Device(progress(5, "level_1" to 55_000, "level_2" to 70_000))
                val phoneSync = ProgressSyncManager(phone, remote, auth)
                val tabletSync = ProgressSyncManager(tablet, remote, auth)

                phoneSync.sync()
                tabletSync.sync()
                phoneSync.sync()

                val everything = progress(5, "level_1" to 40_000, "level_2" to 70_000)
                assertEquals(everything, phone.progress.value)
                assertEquals(everything, tablet.progress.value)
            }
        }

    @Test
    fun aWinAfterSyncingIsUploadedByTheNextSync() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val phone = Device(progress(1))
                val sync = ProgressSyncManager(phone, remote, auth)
                sync.sync()

                phone.recordCompletion(CompletionResult("level_1", 2, 40_000, 5))
                sync.sync()

                val uid = auth.ensureSignedIn().getOrThrow()
                val cloud = remote.update(uid) { checkNotNull(it) }
                assertEquals(40_000L, cloud.bestFor("level_1")?.timeMillis)
                assertEquals(3, cloud.highestUnlockedWeek)
            }
        }

    private companion object {
        const val TIMEOUT = 30_000L
    }
}
