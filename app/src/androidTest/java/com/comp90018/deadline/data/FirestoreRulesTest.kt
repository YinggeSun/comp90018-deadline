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

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
