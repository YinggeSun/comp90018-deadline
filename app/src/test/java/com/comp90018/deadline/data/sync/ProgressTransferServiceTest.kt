package com.comp90018.deadline.data.sync

import com.comp90018.deadline.data.fake.FakeAuthRepository
import com.comp90018.deadline.data.fake.FakeProgressRepository
import com.comp90018.deadline.data.remote.firebase.TransferStore
import com.comp90018.deadline.data.remote.firebase.TransferStoreException
import com.comp90018.deadline.domain.progress.CreateCodeResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.progress.RedeemResult
import com.comp90018.deadline.domain.progress.TransferCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTransferServiceTest {
    /** In-memory codes with switches for Firestore's failure modes. */
    private class FakeStore : TransferStore {
        val codes = mutableMapOf<String, Pair<String, PlayerProgress>>()
        val expiries = mutableMapOf<String, Long>()
        var offline = false
        var hangs = false

        override suspend fun create(
            code: String,
            ownerId: String,
            progress: PlayerProgress,
            expiresAtMillis: Long,
        ) {
            if (hangs) awaitCancellation()
            if (offline) throw TransferStoreException(isOffline = true)
            if (code in codes) throw TransferStoreException(isOffline = false)
            codes[code] = ownerId to progress
            expiries[code] = expiresAtMillis
        }

        override suspend fun read(code: String): PlayerProgress? {
            if (hangs) awaitCancellation()
            if (offline) throw TransferStoreException(isOffline = true)
            return codes[code]?.second
        }
    }

    private val store = FakeStore()
    private var syncs = 0
    private var syncResult: SyncResult = SyncResult.Synced(PlayerProgress())

    private fun progress(
        week: Int,
        vararg bests: Pair<String, Long>,
    ) = PlayerProgress(
        completedLevelIds = bests.map { it.first }.toSet(),
        highestUnlockedWeek = week,
        personalBests = bests.associate { (level, time) -> level to PersonalBest(level, time, 1) },
    )

    private fun service(
        local: FakeProgressRepository,
        user: String? = "me",
        codes: Iterator<String> = listOf("K7QM3XPD", "ABCD2345", "WXYZ6789").iterator(),
    ) = ProgressTransferService(
        local = local,
        store = store,
        auth = FakeAuthRepository(userIdOnSignIn = user),
        sync = {
            syncs++
            syncResult
        },
        nowMillis = { 1_000L },
        newCode = { codes.next() },
    )

    @Test
    fun createdCodeHoldsLocalProgressAndExpiresWithinADay() =
        runTest {
            val result = service(FakeProgressRepository(progress(3, "level_1" to 40_000))).createCode()

            assertTrue(result is CreateCodeResult.Created)
            result as CreateCodeResult.Created
            assertEquals("K7QM-3XPD", result.code)
            assertEquals("me" to progress(3, "level_1" to 40_000), store.codes["K7QM3XPD"])
            assertTrue(result.expiresAtMillis in 1_000L..1_000L + TransferCode.VALID_FOR_MILLIS)
            assertEquals(1, syncs)
        }

    @Test
    fun aClashingCodeIsReplacedByANewOne() =
        runTest {
            store.codes["K7QM3XPD"] = "someone" to PlayerProgress()

            val result = service(FakeProgressRepository(progress(3))).createCode() as CreateCodeResult.Created

            assertEquals("ABCD-2345", result.code)
        }

    @Test
    fun creatingOfflineReportsOffline() =
        runTest {
            syncResult = SyncResult.Offline
            assertEquals(CreateCodeResult.Offline, service(FakeProgressRepository(progress(3))).createCode())

            syncResult = SyncResult.Synced(PlayerProgress())
            store.hangs = true
            assertEquals(CreateCodeResult.Offline, service(FakeProgressRepository(progress(3))).createCode())
        }

    @Test
    fun redeemingMergesTheCodesProgressAndSyncs() =
        runTest {
            store.codes["K7QM3XPD"] = "old-phone" to progress(5, "level_1" to 40_000, "level_2" to 70_000)
            val newPhone = FakeProgressRepository(progress(3, "level_1" to 35_000))

            val result = service(newPhone, user = "new-phone").redeem("k7qm-3xpd")

            val expected = progress(5, "level_1" to 35_000, "level_2" to 70_000)
            assertEquals(RedeemResult.Redeemed(expected), result)
            assertEquals(expected, newPhone.progress.first())
            assertEquals(1, syncs)
        }

    @Test
    fun movingToAFreshInstallRestoresEverything() =
        runTest {
            val oldPhone = FakeProgressRepository(progress(7, "level_1" to 40_000, "level_2" to 70_000, "level_3" to 95_000))
            val code = (service(oldPhone, user = "old").createCode() as CreateCodeResult.Created).code

            val freshInstall = FakeProgressRepository()
            service(freshInstall, user = "new").redeem(code)

            assertEquals(oldPhone.progress.first(), freshInstall.progress.first())
        }

    @Test
    fun unknownOrExpiredCodesAreNotFoundAndChangeNothing() =
        runTest {
            val local = FakeProgressRepository(progress(3, "level_1" to 40_000))

            assertEquals(RedeemResult.NotFound, service(local).redeem("ZZZZ-2222"))
            assertEquals(progress(3, "level_1" to 40_000), local.progress.first())
            assertEquals(0, syncs)
        }

    @Test
    fun impossibleCodesAreRejectedWithoutGoingOnline() =
        runTest {
            store.offline = true

            assertEquals(RedeemResult.InvalidFormat, service(FakeProgressRepository()).redeem("K7QM-3XP0"))
            assertEquals(RedeemResult.InvalidFormat, service(FakeProgressRepository()).redeem("hello"))
        }

    @Test
    fun redeemingOfflineReportsOfflineAndChangesNothing() =
        runTest {
            store.offline = true
            val local = FakeProgressRepository(progress(3))

            assertEquals(RedeemResult.Offline, service(local).redeem("K7QM-3XPD"))
            assertEquals(RedeemResult.Offline, service(local, user = null).redeem("K7QM-3XPD"))
            assertEquals(progress(3), local.progress.first())
        }
}
