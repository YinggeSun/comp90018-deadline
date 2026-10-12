package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProgressDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreTransferDataSource
import com.comp90018.deadline.data.remote.firebase.TransferStoreException
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
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Moves progress between two devices (two separate anonymous accounts) with a transfer code,
 * against the Firebase Emulator Suite (see [FirebaseEmulator]) and the real rules. Fails when
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
        val db: FirebaseFirestore = FirebaseFirestore.getInstance(app)
        val auth = AuthRepositoryImpl(FirebaseAuthDataSource(FirebaseAuth.getInstance(app)), scope)
        val local = InMemoryProgressRepository(initial)
        val cloud = FirestoreProgressDataSource(db)
        private val sync = ProgressSyncManager(local, cloud, auth)
        val transferStore = FirestoreTransferDataSource(db)
        val transfer = ProgressTransferService(local, transferStore, auth, sync::sync)

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
        assertTrue("Required Firebase emulators are unavailable", FirebaseEmulator.isRunning())
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
    fun anExpiredCodeIsNotReadFromTheOfflineCache() =
        runBlocking {
            withTimeout(TIMEOUT) {
                val oldPhone = Device(progress(3, "level_1" to 40_000))
                val newPhone = Device(PlayerProgress())
                val ownerId = oldPhone.auth.ensureSignedIn().getOrThrow()
                newPhone.auth.ensureSignedIn().getOrThrow()
                oldPhone.transferStore.create(
                    "K7QM3XPD",
                    ownerId,
                    progress(3, "level_1" to 40_000),
                    System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1),
                )

                // Read while valid, so the new phone's Firestore cache holds the code.
                assertEquals(progress(3, "level_1" to 40_000), newPhone.transferStore.read("K7QM3XPD"))
                val document = newPhone.db.collection(FirestoreTransferDataSource.COLLECTION).document("K7QM3XPD")
                val cached = document.get(Source.CACHE).await()
                assertTrue("the valid code must be in the reader's cache", cached.exists() && cached.metadata.isFromCache)
                newPhone.db.disableNetwork().await()
                // Control server-side expiry rather than racing emulator latency against a three-second TTL.
                // Only the local emulator fixture changes; production reads still enforce the real rules.
                FirebaseEmulator.expireTransferAsAdmin("K7QM3XPD")
                assertEquals(
                    "expiry must not erase the cache used by this regression",
                    cached.data,
                    document.get(Source.CACHE).await().data,
                )

                val offline = runCatching { newPhone.transferStore.read("K7QM3XPD") }
                assertTrue("an expired code must not be served from the cache", offline.getOrNull() == null)
                assertEquals(true, (offline.exceptionOrNull() as? TransferStoreException)?.isOffline)
                assertEquals(RedeemResult.Offline, newPhone.transfer.redeem("K7QM-3XPD"))
                assertEquals(PlayerProgress(), newPhone.local.progress.value)

                newPhone.db.enableNetwork().await()
                assertEquals(RedeemResult.NotFound, newPhone.transfer.redeem("K7QM-3XPD"))
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
