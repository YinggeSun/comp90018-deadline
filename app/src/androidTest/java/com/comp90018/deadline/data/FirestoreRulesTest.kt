package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks `firestore.rules` directly against the Firebase Emulator Suite (see [FirebaseEmulator]),
 * writing raw documents rather than going through the app, because a modified client could send
 * anything. Each forbidden case changes exactly one thing from a valid entry. Skipped when the
 * emulators are not running.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val apps = mutableListOf<FirebaseApp>()

    private inner class Player(
        signedIn: Boolean = true,
    ) {
        private val app = FirebaseEmulator.newApp(context).also { apps += it }
        val db: FirebaseFirestore = FirebaseFirestore.getInstance(app)
        val uid: String =
            if (signedIn) {
                runBlocking { checkNotNull(FirebaseAuth.getInstance(app).signInAnonymously().await().user).uid }
            } else {
                "nobody"
            }

        fun entry(level: String = "level_1") = db.document("leaderboard/$level/entries/$uid")

        /** A document exactly as the app writes it; tests change one field at a time. */
        fun valid(
            timeMillis: Long = 20_000,
            week: Int = 2,
        ): MutableMap<String, Any> =
            mutableMapOf(
                "uid" to uid,
                "nickname" to "Lav",
                "timeMillis" to timeMillis,
                "week" to week,
                "submittedAt" to FieldValue.serverTimestamp(),
            )
    }

    /**
     * A client-chosen timestamp an hour in the past, the realistic way to forge one. A timestamp
     * of "now" can equal the emulator's server time to the millisecond, because the Android
     * emulator shares the host's clock, which would make such tests flaky.
     */
    private fun backdated() = Timestamp(java.util.Date(System.currentTimeMillis() - 60 * 60 * 1000L))

    /** True if the rules allowed [operation]; false if they refused it. */
    private fun allowed(operation: suspend () -> Unit): Boolean =
        runBlocking {
            withTimeout(TIMEOUT) {
                try {
                    operation()
                    true
                } catch (error: FirebaseFirestoreException) {
                    if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw error
                    false
                }
            }
        }

    @Before
    fun requireEmulators() {
        assumeTrue("Firebase emulators are not running on the host", FirebaseEmulator.isRunning())
        FirebaseEmulator.clearFirestore()
    }

    @After
    fun tearDown() = apps.forEach { it.delete() }

    // Allowed

    @Test
    fun validEntryIsAccepted() {
        val player = Player()
        assertTrue(allowed { player.entry().set(player.valid()).await() })
    }

    @Test
    fun timesAtTheFloorAreAcceptedForTheFirstAndLastLevel() {
        val player = Player()
        assertTrue(allowed { player.entry("level_1").set(player.valid(timeMillis = 4_500, week = 2)).await() })
        assertTrue(allowed { player.entry("level_6").set(player.valid(timeMillis = 15_750, week = 12)).await() })
    }

    @Test
    fun fasterUpdateOfOwnEntryIsAccepted() {
        val player = Player()
        assertTrue(allowed { player.entry().set(player.valid(timeMillis = 30_000)).await() })
        assertTrue(allowed { player.entry().set(player.valid(timeMillis = 20_000)).await() })
    }

    @Test
    fun signedInPlayerCanReadAKnownLevel() {
        val player = Player()
        assertTrue(allowed { player.db.collection("leaderboard/level_1/entries").get().await() })
    }

    @Test
    fun twentyCharacterNicknameIsAccepted() {
        val player = Player()
        assertTrue(allowed { player.entry().set(player.valid() + ("nickname" to "x".repeat(20))).await() })
    }

    // Refused: who may write

    @Test
    fun anotherPlayersEntryIsRefused() {
        val player = Player()
        val forged = player.valid().apply { put("uid", "someone-else") }
        assertFalse(allowed { player.db.document("leaderboard/level_1/entries/someone-else").set(forged).await() })
    }

    @Test
    fun uidFieldMustMatchTheDocument() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid().apply { put("uid", "someone-else") }).await() })
    }

    @Test
    fun signedOutWritesAndReadsAreRefused() {
        val stranger = Player(signedIn = false)
        assertFalse(allowed { stranger.entry().set(stranger.valid()).await() })
        assertFalse(allowed { stranger.db.collection("leaderboard/level_1/entries").get().await() })
    }

    @Test
    fun deletingOwnEntryIsRefused() {
        val player = Player()
        player.entry().set(player.valid()).let { runBlocking { it.await() } }
        assertFalse(allowed { player.entry().delete().await() })
    }

    @Test
    fun slowerOrEqualUpdateIsRefused() {
        val player = Player()
        assertTrue(allowed { player.entry().set(player.valid(timeMillis = 20_000)).await() })
        assertFalse(allowed { player.entry().set(player.valid(timeMillis = 25_000)).await() })
        assertFalse(allowed { player.entry().set(player.valid(timeMillis = 20_000)).await() })
    }

    @Test
    fun anythingOutsideTheLeaderboardIsRefused() {
        val player = Player()
        assertFalse(allowed { player.db.document("players/${player.uid}").set(mapOf("x" to 1)).await() })
        assertFalse(allowed { player.db.document("players/${player.uid}").get().await() })
    }

    // Refused: level, week and time

    @Test
    fun unknownLevelIsRefused() {
        val player = Player()
        assertFalse(allowed { player.entry("level_7").set(player.valid(week = 14)).await() })
        assertFalse(allowed { player.db.collection("leaderboard/level_7/entries").get().await() })
    }

    @Test
    fun weekMustMatchTheLevel() {
        val player = Player()
        assertFalse(allowed { player.entry("level_1").set(player.valid(week = 4)).await() })
    }

    @Test
    fun timesBelowTheFloorAreRefused() {
        val player = Player()
        assertFalse(allowed { player.entry("level_1").set(player.valid(timeMillis = 4_499, week = 2)).await() })
        assertFalse(allowed { player.entry("level_6").set(player.valid(timeMillis = 15_749, week = 12)).await() })
        assertFalse(allowed { player.entry("level_1").set(player.valid(timeMillis = 1, week = 2)).await() })
    }

    @Test
    fun timesAboveADayAreRefused() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid(timeMillis = 86_400_001)).await() })
    }

    // Refused: document shape and types

    @Test
    fun missingFieldIsRefused() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid().apply { remove("week") }).await() })
    }

    @Test
    fun extraFieldIsRefused() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid() + ("score" to 999)).await() })
    }

    @Test
    fun wrongTypesAreRefused() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid() + ("timeMillis" to "20000")).await() })
        assertFalse(allowed { player.entry().set(player.valid() + ("timeMillis" to 20_000.5)).await() })
        assertFalse(allowed { player.entry().set(player.valid() + ("week" to "2")).await() })
        assertFalse(allowed { player.entry().set(player.valid() + ("nickname" to 5)).await() })
    }

    @Test
    fun emptyBlankOrLongNicknamesAreRefused() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid() + ("nickname" to "")).await() })
        assertFalse(allowed { player.entry().set(player.valid() + ("nickname" to "   ")).await() })
        assertFalse(allowed { player.entry().set(player.valid() + ("nickname" to "x".repeat(21))).await() })
    }

    @Test
    fun clientChosenTimestampIsRefused() {
        val player = Player()
        assertFalse(allowed { player.entry().set(player.valid() + ("submittedAt" to backdated())).await() })
    }

    // Progress (#39)

    private fun Player.progressDoc(of: String = uid) = db.document("progress/$of")

    /** A progress document exactly as the app writes it; tests change one thing at a time. */
    private fun validProgress(
        week: Int = 3,
        completed: List<String> = listOf("level_1"),
        bests: Map<String, Long> = mapOf("level_1" to 40_000L),
    ): MutableMap<String, Any> =
        mutableMapOf(
            "completedLevelIds" to completed,
            "highestUnlockedWeek" to week,
            "personalBests" to bests.mapValues { (_, time) -> mapOf("timeMillis" to time, "achievedAtMillis" to 1L) },
            "lastModifiedMillis" to 1L,
            "updatedAt" to FieldValue.serverTimestamp(),
        )

    @Test
    fun ownProgressCanBeCreatedReadAndImproved() {
        val player = Player()
        assertTrue(allowed { player.progressDoc().set(validProgress()).await() })
        assertTrue(allowed { player.progressDoc().get().await() })
        assertTrue(
            allowed {
                player.progressDoc().set(
                    validProgress(
                        week = 5,
                        completed = listOf("level_1", "level_2"),
                        bests = mapOf("level_1" to 30_000L, "level_2" to 70_000L),
                    ),
                ).await()
            },
        )
    }

    @Test
    fun anotherPlayersProgressIsRefused() {
        val owner = Player()
        owner.progressDoc().set(validProgress()).let { runBlocking { it.await() } }
        val other = Player()
        assertFalse(allowed { other.progressDoc(of = owner.uid).get().await() })
        assertFalse(allowed { other.progressDoc(of = owner.uid).set(validProgress(week = 12)).await() })
        assertFalse(allowed { Player(signedIn = false).progressDoc(of = owner.uid).get().await() })
    }

    @Test
    fun progressCannotBeCreatedForAnotherPlayer() {
        val player = Player()
        assertFalse(allowed { player.progressDoc(of = "someone-without-progress").set(validProgress()).await() })
    }

    @Test
    fun progressCannotGetWorse() {
        val player = Player()
        assertTrue(
            allowed {
                player.progressDoc().set(
                    validProgress(week = 5, completed = listOf("level_1", "level_2"), bests = mapOf("level_1" to 30_000L)),
                ).await()
            },
        )
        assertFalse(
            allowed {
                player.progressDoc().set(
                    validProgress(week = 4, completed = listOf("level_1", "level_2"), bests = mapOf("level_1" to 30_000L)),
                ).await()
            },
        )
        assertFalse(
            allowed {
                player.progressDoc().set(
                    validProgress(week = 5, completed = listOf("level_1"), bests = mapOf("level_1" to 30_000L)),
                ).await()
            },
        )
        assertFalse(
            allowed {
                player.progressDoc().set(
                    validProgress(week = 5, completed = listOf("level_1", "level_2"), bests = mapOf("level_1" to 35_000L)),
                ).await()
            },
        )
        assertFalse(
            allowed {
                player.progressDoc().set(validProgress(week = 5, completed = listOf("level_1", "level_2"), bests = emptyMap())).await()
            },
        )
        assertFalse(allowed { player.progressDoc().delete().await() })
    }

    @Test
    fun invalidProgressIsRefused() {
        val player = Player()
        assertFalse(allowed { player.progressDoc().set(validProgress() + ("extra" to 1)).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress().apply { remove("lastModifiedMillis") }).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress(week = 13)).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress(week = 0)).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress(completed = listOf("level_9"))).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress(bests = mapOf("level_9" to 40_000L))).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress(bests = mapOf("level_1" to 4_499L))).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress() + ("highestUnlockedWeek" to "3")).await() })
        assertFalse(allowed { player.progressDoc().set(validProgress() + ("updatedAt" to backdated())).await() })
    }

    // Transfer codes (#39)

    private fun Player.transferDoc(code: String = "K7QM3XPD") = db.document("transfers/$code")

    /** A transfer document exactly as the app writes it; tests change one thing at a time. */
    private fun Player.validTransfer(expiresInMillis: Long = 23 * 60 * 60 * 1000L): MutableMap<String, Any> =
        (validProgress() - "updatedAt").toMutableMap().apply {
            put("ownerId", uid)
            put("createdAt", FieldValue.serverTimestamp())
            put("expiresAt", Timestamp(java.util.Date(System.currentTimeMillis() + expiresInMillis)))
        }

    @Test
    fun aCodeCanBeCreatedAndReadByAnotherPlayerWhoKnowsIt() {
        val creator = Player()
        assertTrue(allowed { creator.transferDoc().set(creator.validTransfer()).await() })
        val other = Player()
        assertTrue(allowed { other.transferDoc().get().await() })
    }

    @Test
    fun codesCannotBeListedChangedOrDeleted() {
        val creator = Player()
        creator.transferDoc().set(creator.validTransfer()).let { runBlocking { it.await() } }
        assertFalse(allowed { creator.db.collection("transfers").get().await() })
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer() + ("highestUnlockedWeek" to 12)).await() })
        assertFalse(allowed { creator.transferDoc().delete().await() })
    }

    @Test
    fun signedOutAndMissingCodesCannotBeRead() {
        val creator = Player()
        creator.transferDoc().set(creator.validTransfer()).let { runBlocking { it.await() } }
        assertFalse(allowed { Player(signedIn = false).transferDoc().get().await() })
        assertFalse(allowed { creator.transferDoc("ZZZZ2222").get().await() })
    }

    @Test
    fun anExpiredCodeCannotBeRead() {
        val creator = Player()
        assertTrue(allowed { creator.transferDoc().set(creator.validTransfer(expiresInMillis = 3_000)).await() })
        Thread.sleep(5_000)
        assertFalse(allowed { Player().transferDoc().get().await() })
    }

    @Test
    fun invalidCodesAreRefused() {
        val creator = Player()
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer() + ("ownerId" to "someone-else")).await() })
        assertFalse(allowed { creator.transferDoc("k7qm3xpd").set(creator.validTransfer()).await() })
        assertFalse(allowed { creator.transferDoc("K7QM3XP0").set(creator.validTransfer()).await() })
        assertFalse(allowed { creator.transferDoc("K7QM3XP").set(creator.validTransfer()).await() })
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer() + ("highestUnlockedWeek" to 13)).await() })
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer() + ("extra" to 1)).await() })
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer() + ("createdAt" to backdated())).await() })
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer(expiresInMillis = 25 * 60 * 60 * 1000L)).await() })
        assertFalse(allowed { creator.transferDoc().set(creator.validTransfer(expiresInMillis = -60_000)).await() })
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
