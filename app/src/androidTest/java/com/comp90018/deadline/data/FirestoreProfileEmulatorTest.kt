package com.comp90018.deadline.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProfileDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProgressDataSource
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Runs exclusively against the existing local Auth/Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class FirestoreProfileEmulatorTest {
    private val apps = mutableListOf<FirebaseApp>()

    private fun app() = FirebaseEmulator.newApp(ApplicationProvider.getApplicationContext<Context>()).also { apps += it }

    @Before fun requireEmulators() {
        assertTrue("Required Firebase emulators are unavailable", FirebaseEmulator.isRunning())
    }

    @After fun close() {
        apps.forEach { it.delete() }
    }

    @Test fun linkingKeepsCloudProgressAndProfileIsIdempotentAndPrivate() =
        runBlocking {
            withTimeout(30_000) {
                val ownerApp = app()
                val auth = FirebaseAuthDataSource(FirebaseAuth.getInstance(ownerApp))
                val db = FirebaseFirestore.getInstance(ownerApp)
                val uid = auth.signInAnonymously()
                val progress = FirestoreProgressDataSource(db)
                progress.update(uid) { PlayerProgress(setOf("level_1"), 3) }
                val email = "${UUID.randomUUID()}@example.test"
                val linked = auth.link(email, "emulator-password")
                assertEquals(uid, linked.uid)
                val profiles = FirestoreProfileDataSource(db)
                profiles.loadOrCreate(linked, "Student")
                val original = db.document("users/$uid").get(Source.SERVER).await()
                profiles.loadOrCreate(linked, "Updated", recordLogin = false)
                val updated = db.document("users/$uid").get(Source.SERVER).await()
                assertEquals(original.getTimestamp("createdAt"), updated.getTimestamp("createdAt"))
                assertEquals(original.getTimestamp("lastLoginAt"), updated.getTimestamp("lastLoginAt"))
                assertEquals("Updated", updated.getString("nickname"))
                assertEquals(setOf("level_1"), progress.update(uid) { checkNotNull(it) }.completedLevelIds)
                assertFalse(updated.contains("password"))
                val strangerApp = app()
                FirebaseAuth.getInstance(strangerApp).signInAnonymously().await()
                val stranger = FirebaseFirestore.getInstance(strangerApp)
                refused { stranger.document("users/$uid").get(Source.SERVER).await() }
                refused { stranger.document("users/$uid").update("nickname", "Stolen").await() }
                refused { stranger.document("progress/$uid").get(Source.SERVER).await() }
                refused { db.collection("users").get(Source.SERVER).await() }
                refused { db.document("users/$uid").update("email", "forged@example.test").await() }
                refused { db.document("users/$uid").update("password", "forbidden").await() }
                refused { db.document("users/$uid").update("createdAt", FieldValue.serverTimestamp()).await() }
                val backdated = Timestamp(java.util.Date(System.currentTimeMillis() - 60_000))
                refused { db.document("users/$uid").update("updatedAt", backdated).await() }
                refused { db.document("users/$uid").update("lastLoginAt", backdated, "updatedAt", FieldValue.serverTimestamp()).await() }
                refused { db.document("users/$uid").update("nickname", "   ", "updatedAt", FieldValue.serverTimestamp()).await() }
                refused { db.document("users/$uid").update("accountType", "guest", "updatedAt", FieldValue.serverTimestamp()).await() }
                refused { db.document("users/$uid").delete().await() }
                // A different registered user has no more access than the anonymous stranger.
                FirebaseAuth.getInstance(strangerApp).createUserWithEmailAndPassword(
                    "${UUID.randomUUID()}@example.test",
                    "emulator-password",
                ).await()
                refused { stranger.document("users/$uid").get(Source.SERVER).await() }
                refused { stranger.document("users/$uid").set(original.data!!).await() }
                auth.signOut()
                refused { db.document("users/$uid").get(Source.SERVER).await() }
                assertEquals(uid, auth.login(email, "emulator-password").uid)
            }
        }

    @Test fun anonymousCannotCreateRegisteredProfile() =
        runBlocking {
            withTimeout(20_000) {
                val guestApp = app()
                val uid = FirebaseAuth.getInstance(guestApp).signInAnonymously().await().user!!.uid
                refused {
                    FirebaseFirestore.getInstance(guestApp).document("users/$uid").set(
                        mapOf(
                            "nickname" to "Guest",
                            "email" to "forged@example.test",
                            "accountType" to "registered",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "lastLoginAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                }
            }
        }

    @Test fun registeredProfileCreationRejectsClientTimestampsAndMalformedFields() =
        runBlocking {
            withTimeout(20_000) {
                val ownerApp = app()
                val auth = FirebaseAuthDataSource(FirebaseAuth.getInstance(ownerApp))
                val email = "${UUID.randomUUID()}@example.test"
                val account = auth.create(email, "emulator-password")
                val doc = FirebaseFirestore.getInstance(ownerApp).document("users/${account.uid}")
                val valid =
                    mapOf(
                        "nickname" to "Student",
                        "email" to email,
                        "accountType" to "registered",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                val backdated = Timestamp(java.util.Date(System.currentTimeMillis() - 60_000))
                refused { doc.set(valid + ("createdAt" to backdated)).await() }
                refused { doc.set(valid + ("lastLoginAt" to backdated)).await() }
                refused { doc.set(valid + ("email" to "forged@example.test")).await() }
                refused { doc.set(valid + ("nickname" to 1)).await() }
                refused { doc.set(valid + ("nickname" to "x".repeat(21))).await() }
                refused { doc.set(valid + ("password" to "forbidden")).await() }
                doc.set(valid).await()
                assertEquals("Student", doc.get(Source.SERVER).await().getString("nickname"))
            }
        }

    private suspend fun refused(block: suspend () -> Unit) {
        try {
            block()
            fail("Rules should refuse this operation")
        } catch (error: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
        }
    }
}
