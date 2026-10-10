package com.comp90018.deadline.data.sync

import com.comp90018.deadline.data.fake.FakeAuthRepository
import com.comp90018.deadline.data.fake.FakeProgressRepository
import com.comp90018.deadline.data.remote.firebase.RemoteProgressException
import com.comp90018.deadline.data.remote.firebase.RemoteProgressStore
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSyncManagerTest {
    /** In-memory cloud copy with switches for Firestore's failure modes. */
    private class FakeRemote : RemoteProgressStore {
        val stored = mutableMapOf<String, PlayerProgress>()
        var hangs = false
        var refuses = false
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        var concurrent = 0
        var maxConcurrent = 0

        override suspend fun update(
            userId: String,
            transform: (PlayerProgress?) -> PlayerProgress,
        ): PlayerProgress {
            calls++
            concurrent++
            maxConcurrent = maxOf(maxConcurrent, concurrent)
            try {
                if (hangs) awaitCancellation()
                if (refuses) throw RemoteProgressException(isRefused = true)
                gate?.await()
                return transform(stored[userId]).also { stored[userId] = it }
            } finally {
                concurrent--
            }
        }
    }

    private val remote = FakeRemote()
    private val auth = FakeAuthRepository(userIdOnSignIn = "me")

    private fun progress(
        week: Int = 1,
        vararg bests: Pair<String, Long>,
    ) = PlayerProgress(
        completedLevelIds = bests.map { it.first }.toSet(),
        highestUnlockedWeek = week,
        personalBests = bests.associate { (level, time) -> level to PersonalBest(level, time, 0) },
    )

    @Test
    fun firstSyncUploadsLocalProgress() =
        runTest {
            val local = FakeProgressRepository(progress(3, "level_1" to 40_000))

            val result = ProgressSyncManager(local, remote, auth).sync()

            assertEquals(SyncResult.Synced(progress(3, "level_1" to 40_000)), result)
            assertEquals(progress(3, "level_1" to 40_000), remote.stored["me"])
        }

    @Test
    fun betterCloudProgressIsMergedIntoLocal() =
        runTest {
            remote.stored["me"] = progress(5, "level_1" to 30_000, "level_2" to 80_000)
            val local = FakeProgressRepository(progress(3, "level_1" to 40_000))

            ProgressSyncManager(local, remote, auth).sync()

            assertEquals(progress(5, "level_1" to 30_000, "level_2" to 80_000), local.progress.first())
        }

    @Test
    fun betterLocalProgressIsNeverOverwrittenByTheCloud() =
        runTest {
            remote.stored["me"] = progress(2, "level_1" to 90_000)
            val local = FakeProgressRepository(progress(5, "level_1" to 30_000, "level_2" to 80_000))

            ProgressSyncManager(local, remote, auth).sync()

            assertEquals(progress(5, "level_1" to 30_000, "level_2" to 80_000), local.progress.first())
            assertEquals(progress(5, "level_1" to 30_000, "level_2" to 80_000), remote.stored["me"])
        }

    @Test
    fun twoDevicesSharingTheCloudCopyEndUpWithEverything() =
        runTest {
            val phone = FakeProgressRepository(progress(3, "level_1" to 40_000))
            val tablet = FakeProgressRepository(progress(5, "level_2" to 70_000))
            val phoneSync = ProgressSyncManager(phone, remote, auth)
            val tabletSync = ProgressSyncManager(tablet, remote, auth)

            phoneSync.sync()
            tabletSync.sync()
            phoneSync.sync()

            val everything = progress(5, "level_1" to 40_000, "level_2" to 70_000)
            assertEquals(everything, phone.progress.first())
            assertEquals(everything, tablet.progress.first())
            assertEquals(everything, remote.stored["me"])
        }

    @Test
    fun offlineLeavesLocalProgressUntouched() =
        runTest {
            remote.hangs = true
            val local = FakeProgressRepository(progress(3, "level_1" to 40_000))

            assertEquals(SyncResult.Offline, ProgressSyncManager(local, remote, auth).sync())
            assertEquals(progress(3, "level_1" to 40_000), local.progress.first())
        }

    @Test
    fun aSyncAfterReconnectingUploadsWhatWasPlayedOffline() =
        runTest {
            val local = FakeProgressRepository(progress(1))
            val sync = ProgressSyncManager(local, remote, auth)
            remote.hangs = true
            local.recordCompletion(CompletionResult("level_1", 2, 40_000, 5))
            assertEquals(SyncResult.Offline, sync.sync())

            remote.hangs = false
            sync.sync()

            assertEquals(40_000L, remote.stored["me"]?.bestFor("level_1")?.timeMillis)
            assertEquals(3, remote.stored["me"]?.highestUnlockedWeek)
        }

    @Test
    fun refusalAndMissingIdentityAreReportedWithoutTouchingProgress() =
        runTest {
            val local = FakeProgressRepository(progress(3, "level_1" to 40_000))
            remote.refuses = true
            assertEquals(SyncResult.Refused, ProgressSyncManager(local, remote, auth).sync())

            remote.refuses = false
            assertEquals(SyncResult.NotSignedIn, ProgressSyncManager(local, remote, FakeAuthRepository(userIdOnSignIn = null)).sync())

            assertEquals(progress(3, "level_1" to 40_000), local.progress.first())
            assertNull(remote.stored["me"])
        }

    @Test
    fun aWinRecordedDuringASyncIsKept() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            val local = FakeProgressRepository(progress(1))
            val syncing = async { ProgressSyncManager(local, remote, auth).sync() }
            runCurrent()

            local.recordCompletion(CompletionResult("level_1", 2, 40_000, 5))
            gate.complete(Unit)
            syncing.await()

            assertEquals(40_000L, local.progress.first().bestFor("level_1")?.timeMillis)
        }

    @Test
    fun syncsRunOneAtATime() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            remote.gate = gate
            val sync = ProgressSyncManager(FakeProgressRepository(progress(2)), remote, auth)

            val syncs = List(3) { async { sync.sync() } }
            runCurrent()
            gate.complete(Unit)
            syncs.awaitAll()

            assertEquals(3, remote.calls)
            assertEquals(1, remote.maxConcurrent)
            assertTrue(syncs.all { it.await() is SyncResult.Synced })
        }
}
