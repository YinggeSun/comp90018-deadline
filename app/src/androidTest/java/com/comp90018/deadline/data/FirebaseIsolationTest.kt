package com.comp90018.deadline.data

import androidx.test.core.app.ApplicationProvider
import com.comp90018.deadline.domain.repository.BalanceSource
import com.comp90018.deadline.testing.EmulatorApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseIsolationTest {
    @Test
    fun defaultAndNamedClientsUseOnlyDemoEmulators() =
        runBlocking {
            withTimeout(30_000) {
                val context = ApplicationProvider.getApplicationContext<EmulatorApplication>()
                val named = FirebaseEmulator.newApp(context)
                try {
                    for (app in listOf(FirebaseApp.getInstance(), named)) {
                        assertEquals("demo-deadline", app.options.projectId)
                        assertEquals("emulator-only", app.options.apiKey)
                        assertEquals("1:1:android:1", app.options.applicationId)
                        val auth = FirebaseAuth.getInstance(app)
                        val user = auth.currentUser ?: auth.signInAnonymously().await().user!!
                        val claims = user.getIdToken(true).await().claims
                        assertEquals("demo-deadline", claims["aud"])
                        assertEquals("https://securetoken.google.com/demo-deadline", claims["iss"])
                        val db = FirebaseFirestore.getInstance(app)
                        assertEquals("10.0.2.2:8080", db.firestoreSettings.host)
                        assertFalse(db.firestoreSettings.isSslEnabled)
                        val ref = db.document("progress/${user.uid}")
                        // Read the server, not the offline cache. A fresh named client exercises a write too.
                        if (app == named) {
                            ref.set(
                                mapOf(
                                    "completedLevelIds" to emptyList<String>(),
                                    "highestUnlockedWeek" to 1,
                                    "personalBests" to emptyMap<String, Any>(),
                                    "lastModifiedMillis" to 1L,
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                ),
                            ).await()
                            assertTrue(ref.get(Source.SERVER).await().exists())
                        } else {
                            ref.get(Source.SERVER).await()
                        }
                    }
                } finally {
                    named.delete()
                }
            }
        }

    @Test
    fun remoteConfigSdkIsUnavailableAndApplicationUsesLocalDefaults() =
        runBlocking {
            assertTrue(runCatching { FirebaseRemoteConfig.getInstance() }.isFailure)
            val repository = ApplicationProvider.getApplicationContext<EmulatorApplication>().container.configRepository
            assertEquals(BalanceSource.DEFAULTS, repository.refresh())
        }
}
