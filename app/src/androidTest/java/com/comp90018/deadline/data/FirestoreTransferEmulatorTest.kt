package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProgressDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreTransferDataSource
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.sync.ProgressSyncManager
import com.comp90018.deadline.data.sync.ProgressTransferService
import com.comp90018.deadline.domain.progress.CreateCodeResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.progress.RedeemResult
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Moves progress between two devices (two separate anonymous accounts) with a transfer code,
 * against the Firebase Emulator Suite (see [FirebaseEmulator]) and the real rules. Skipped when
 * the emulators are not running.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreTransferEmulatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apps = mutableListOf<FirebaseApp>()

    private inner class Device(
        initial: PlayerProgress,
    ) {
        private val app = FirebaseEmulator.newApp(context).also { apps += it }
        private val db = FirebaseFirestore.getInstance(app)
        val auth = AuthRepositoryImpl(FirebaseAuthDataSource(FirebaseAuth.getInstance(app)), scope)
        val local = InMemoryProgressRepository(initial)
        val cloud = FirestoreProgressDataSource(db)
        private val sync = ProgressSyncManager(local, cloud, auth)
        val transfer = ProgressTransferService(local, FirestoreTransferDataSource(db), auth, sync::sync)

        suspend fun cloudCopy(): PlayerProgress = cloud.update(auth.ensureSignedIn().getOrThrow()) { checkNotNull(it) }
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
    fun aCodeMovesProgressToANewDeviceAndItsCloudCopy() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val oldPhone = Device(progress(7, "level_1" to 40_000, "level_2" to 70_000, "level_3" to 95_000))
                val newPhone = Device(PlayerProgress())

                val created = oldPhone.transfer.createCode()
                assertTrue(created is CreateCodeResult.Created)
                val result = newPhone.transfer.redeem((created as CreateCodeResult.Created).code)

                val everything = progress(7, "level_1" to 40_000, "level_2" to 70_000, "level_3" to 95_000)
                assertEquals(RedeemResult.Redeemed(everything), result)
                assertEquals(everything, newPhone.local.progress.value)
                assertEquals(everything, newPhone.cloudCopy())
            }
        }

    @Test
    fun redeemingKeepsWhatTheNewDeviceAlreadyHad() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val oldPhone = Device(progress(5, "level_1" to 40_000))
                val newPhone = Device(progress(3, "level_1" to 30_000))

                val code = (oldPhone.transfer.createCode() as CreateCodeResult.Created).code
                newPhone.transfer.redeem(code)

                assertEquals(progress(5, "level_1" to 30_000), newPhone.local.progress.value)
            }
        }

    @Test
    fun anUnknownCodeIsNotFound() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val newPhone = Device(progress(3))

                assertEquals(RedeemResult.NotFound, newPhone.transfer.redeem("ZZZZ-2222"))
                assertEquals(progress(3), newPhone.local.progress.value)
            }
        }

    private companion object {
        const val TIMEOUT = 40_000L
    }
}
