package com.comp90018.deadline.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.comp90018.deadline.data.local.datastore.AccountDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class AccountDataStoreTest {
    @get:Rule
    val disk = DataStoreTestRule()

    @Test
    fun firstGuestUidKeepsQueuedCompletionSessionButLoginAndLogoutInvalidateIt() =
        runBlocking {
            val store = AccountDataStore(disk.dataStore)
            store.activate(null)
            val queuedCompletion = store.sessionVersion
            store.activate("anonymous", migrateLocalGuest = true)
            assertEquals(queuedCompletion, store.sessionVersion)
            store.activate("anonymous") // Credential linking keeps the UID and session.
            assertEquals(queuedCompletion, store.sessionVersion)
            store.activate("registered")
            assertTrue(queuedCompletion != store.sessionVersion)
            val registeredCompletion = store.sessionVersion
            store.activate(null)
            assertTrue(registeredCompletion != store.sessionVersion)
        }

    @Test
    fun namespacesSurviveRestartAndSettingsRemainShared() =
        runBlocking {
            val store = AccountDataStore(disk.dataStore)
            store.activate("account-a")
            ProgressDataSource(store).mergeIn(PlayerProgress(setOf("level_1"), 3))
            SettingsDataSource(store).update { it.copy(nickname = "Private name", hapticsEnabled = false) }
            store.activate("account-b")
            assertEquals(PlayerProgress(), ProgressDataSource(store).progress.first())
            assertEquals(null, SettingsDataSource(store).settings.first().nickname)
            assertFalse(SettingsDataSource(store).settings.first().hapticsEnabled)
            val restored = AccountDataStore(disk.reopen())
            restored.activate("account-a")
            assertEquals(setOf("level_1"), ProgressDataSource(restored).progress.first().completedLevelIds)
            assertEquals("Private name", SettingsDataSource(restored).settings.first().nickname)
            assertTrue(restored.data.first().asMap().keys.none { it.name.contains('/') })
        }

    @Test
    fun mismatchedFirebaseUidCannotReadOrWriteOldPlayersProgress() =
        runBlocking {
            var firebaseUid: String? = "a"
            val store = AccountDataStore(disk.dataStore) { firebaseUid }
            store.activate("a")
            ProgressDataSource(store).mergeIn(PlayerProgress(setOf("level_1"), 3))
            firebaseUid = "b"
            assertEquals(PlayerProgress(), ProgressDataSource(store).progress.first())
            assertTrue(runCatching { ProgressDataSource(store).mergeIn(PlayerProgress()) }.isFailure)
            store.activate("b")
            assertEquals(PlayerProgress(), ProgressDataSource(store).progress.first())
            firebaseUid = "a"
            store.activate("a")
            assertEquals(setOf("level_1"), ProgressDataSource(store).progress.first().completedLevelIds)
        }

    private class FailingStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var failCommit = false
        override val data = delegate.data

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            delegate.updateData {
                val result = transform(it)
                if (failCommit) throw IOException("disk full")
                result
            }
    }

    @Test fun guestMigrationMergesExistingTargetAndIsRepeatableAfterRestart() =
        runBlocking {
            val store = AccountDataStore(disk.dataStore)
            store.activate("target")
            val progress = ProgressDataSource(store)
            progress.mergeIn(
                PlayerProgress(
                    setOf("level_2"),
                    5,
                    mapOf("level_1" to PersonalBest("level_1", 20_000, 2)),
                ),
            )
            SettingsDataSource(store).update { it.copy(nickname = "Target") }
            store.activate(null)
            progress.mergeIn(
                PlayerProgress(
                    setOf("level_1"),
                    3,
                    mapOf("level_1" to PersonalBest("level_1", 30_000, 1)),
                ),
            )
            SettingsDataSource(store).update { it.copy(nickname = "Guest") }
            store.activate("target", migrateLocalGuest = true)
            val migrated = progress.progress.first()
            assertEquals(setOf("level_1", "level_2"), migrated.completedLevelIds)
            assertEquals(5, migrated.highestUnlockedWeek)
            assertEquals(20_000L, migrated.bestFor("level_1")?.timeMillis)
            assertEquals("Target", SettingsDataSource(store).settings.first().nickname)
            store.activate("target", migrateLocalGuest = true)
            assertEquals(migrated, progress.progress.first())
            val reopened = AccountDataStore(disk.reopen())
            reopened.activate("target", migrateLocalGuest = true)
            assertEquals(migrated, ProgressDataSource(reopened).progress.first())
        }

    @Test fun legacyMigrationDoesNotReplaceExistingTargetNamespace() =
        runBlocking {
            disk.dataStore.edit {
                it[stringSetPreferencesKey("progress_completed_level_ids")] = setOf("level_1")
                it[stringSetPreferencesKey("target/progress_completed_level_ids")] = setOf("level_2")
            }
            val store = AccountDataStore(disk.dataStore)
            store.activate("target")
            store.activate("target")
            assertEquals(setOf("level_1", "level_2"), ProgressDataSource(store).progress.first().completedLevelIds)
            assertFalse(disk.dataStore.data.first().asMap().keys.any { it.name == "progress_completed_level_ids" })
        }

    @Test fun interruptedMigrationIsAtomicAndCanRetryWithoutLosingSourceOrTarget() =
        runBlocking {
            val failing = FailingStore(disk.dataStore)
            val store = AccountDataStore(failing)
            store.activate("target")
            ProgressDataSource(store).mergeIn(PlayerProgress(setOf("level_2"), 5))
            store.activate(null)
            ProgressDataSource(store).mergeIn(PlayerProgress(setOf("level_1"), 3))
            failing.failCommit = true
            assertTrue(runCatching { store.activate("target", true) }.isFailure)
            failing.failCommit = false
            val reopened = AccountDataStore(disk.reopen())
            // After process death the caller must explicitly retry migration; normal login never infers it.
            reopened.activate("target", true)
            assertEquals(setOf("level_1", "level_2"), ProgressDataSource(reopened).progress.first().completedLevelIds)
        }

    @Test fun failedActivationCannotMakeNextWriteEraseHiddenOldData() =
        runBlocking {
            val failing = FailingStore(disk.dataStore)
            val store = AccountDataStore(failing) { "old" }
            store.activate("old")
            val progress = ProgressDataSource(store)
            progress.mergeIn(PlayerProgress(setOf("level_1"), 3))
            failing.failCommit = true
            assertTrue(runCatching { store.activate(null) }.isFailure)
            failing.failCommit = false
            assertEquals(PlayerProgress(), progress.progress.first())
            assertTrue(runCatching { progress.mergeIn(PlayerProgress()) }.isFailure)
            store.activate("old")
            assertEquals(setOf("level_1"), progress.progress.first().completedLevelIds)
        }

    @Test fun normalLoginAfterInterruptedMigrationDoesNotImportOfflineGuest() =
        runBlocking {
            val failing = FailingStore(disk.dataStore)
            val store = AccountDataStore(failing)
            store.activate(null)
            ProgressDataSource(store).mergeIn(PlayerProgress(setOf("level_1"), 3))
            failing.failCommit = true
            assertTrue(runCatching { store.activate("new", true) }.isFailure)
            val reopened = AccountDataStore(disk.reopen())
            reopened.activate("unrelated")
            assertEquals(PlayerProgress(), ProgressDataSource(reopened).progress.first())
            reopened.activate(null)
            assertEquals(setOf("level_1"), ProgressDataSource(reopened).progress.first().completedLevelIds)
        }

    @Test fun writeSuspendedAcrossTransitionIsRejectedAtomically() =
        runBlocking {
            val store = AccountDataStore(disk.dataStore)
            store.activate("a")
            ProgressDataSource(store).mergeIn(PlayerProgress(setOf("level_1"), 3))
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val write =
                async(Dispatchers.Default) {
                    runCatching {
                        store.updateData {
                            entered.complete(Unit)
                            release.await()
                            it
                        }
                    }
                }
            entered.await()
            val switching = async(Dispatchers.Default) { store.activate("b") }
            // The selected namespace changes before the queued DataStore edit can commit.
            store.data.first { it.asMap().keys.none { key -> key.name.startsWith("progress_") } }
            release.complete(Unit)
            assertTrue(write.await().isFailure)
            switching.await()
            store.activate("a")
            assertEquals(setOf("level_1"), ProgressDataSource(store).progress.first().completedLevelIds)
        }
}
