package com.comp90018.deadline.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.comp90018.deadline.data.local.DataStoreTestRule
import com.comp90018.deadline.data.local.datastore.AccountDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.remote.firebase.AccountAuthClient
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class GuestMigrationRestartTest {
    @get:Rule val disk = DataStoreTestRule()

    private val saved =
        PlayerProgress(setOf("offline_level"), 5, mapOf("offline_level" to PersonalBest("offline_level", 123, 1)), 10)

    private class Client(var identity: Account = Account()) : AccountAuthClient {
        var creations = 0
        var afterFirebasePersisted: suspend () -> Unit = {}
        override val currentAccount get() = identity
        override val currentUserId get() = identity.uid

        override fun userIdChanges() = flowOf(currentUserId)

        override suspend fun signInAnonymously(): String {
            creations++
            identity = Account("new-guest")
            afterFirebasePersisted()
            return "new-guest"
        }

        override suspend fun create(
            email: String,
            password: String,
        ): Account {
            creations++
            identity = Account("created", email)
            afterFirebasePersisted()
            return identity
        }

        override suspend fun link(
            email: String,
            password: String,
        ): Account = identity.copy(email = email).also { identity = it }

        override suspend fun login(
            email: String,
            password: String,
        ): Account = Account("registered", email).also { identity = it }

        override fun signOut() {
            identity = Account()
        }
    }

    private class FaultStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var beforeCommit = false
        var afterCommit = false
        override val data = delegate.data

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val result =
                delegate.updateData {
                    val next = transform(it)
                    if (beforeCommit) throw IOException("interrupted before atomic commit")
                    next
                }
            if (afterCommit) throw IOException("interrupted before caller acknowledgement")
            return result
        }
    }

    private fun repository(
        client: Client,
        scope: CoroutineScope,
        store: AccountDataStore,
    ) = AuthRepositoryImpl(client, scope, guestMigration = store, activate = { uid, migrate -> store.activate(uid, migrate) })

    private suspend fun seed(
        client: Client,
        backing: DataStore<Preferences> = disk.dataStore,
    ): AccountDataStore =
        AccountDataStore(backing) { client.currentUserId }.also {
            it.activate(null)
            ProgressDataSource(it).mergeIn(saved)
        }

    private suspend fun sourceIsIntact() {
        val fields = disk.dataStore.data.first().asMap().mapKeys { it.key.name }
        assertEquals(saved.completedLevelIds, fields["local-guest/progress_completed_level_ids"])
    }

    @Test fun intentIsCommittedBeforeFirebaseCanPersistUid() =
        runBlocking {
            val client = Client()
            val store = seed(client)
            client.afterFirebasePersisted = {
                val fields = disk.dataStore.data.first().asMap().mapKeys { it.key.name }
                assertEquals("local-guest", fields["guest_migration/source"])
                assertFalse(fields.containsKey("guest_migration/destination"))
                sourceIsIntact()
            }
            assertTrue(repository(client, this, store).ensureSignedIn().isSuccess)
            assertEquals(saved, ProgressDataSource(store).progress.first())
        }

    @Test fun restartBeforeUidConfirmationFailsClosedAndOffersExplicitGuestRecovery() =
        runBlocking {
            val client = Client()
            val store = seed(client)
            client.afterFirebasePersisted = { throw IOException("process stops before SDK result") }
            assertTrue(repository(client, this, store).ensureSignedIn().isFailure)
            val persistedIdentity = Client(client.identity)
            val reopened = AccountDataStore(disk.reopen()) { persistedIdentity.currentUserId }
            val repo = repository(persistedIdentity, this, reopened)
            assertTrue(repo.ensureSignedIn().isFailure)
            assertFalse(repo.account.value.storageReady)
            assertTrue(repo.account.value.guestRecoveryRequired)
            assertTrue(repo.refreshProfile().isFailure)
            sourceIsIntact()
            assertTrue(repo.recoverLocalGuest().isSuccess)
            assertEquals(null, persistedIdentity.currentUserId)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
            assertTrue(repo.account.value.storageReady)
            assertTrue(repo.ensureSignedIn().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun confirmedDestinationRecoversAfterInterruptedTransactionAndRepeatedRestarts() =
        runBlocking {
            val client = Client()
            val faults = FaultStore(disk.dataStore)
            val store = seed(client, faults)
            store.prepare()
            client.identity = Account("new-guest")
            store.confirm("new-guest")
            faults.beforeCommit = true
            assertTrue(runCatching { store.activate("new-guest") }.isFailure)
            sourceIsIntact()
            repeat(3) {
                val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
                val repo = repository(client, this, reopened)
                assertTrue(repo.refreshProfile().isSuccess)
                assertEquals(saved, ProgressDataSource(reopened).progress.first())
                assertTrue(repo.ensureSignedIn().isSuccess)
                assertEquals(saved, ProgressDataSource(reopened).progress.first())
            }
        }

    @Test fun committedMigrationSurvivesLostAcknowledgementWithoutDuplicatingRecords() =
        runBlocking {
            val client = Client()
            val faults = FaultStore(disk.dataStore)
            val store = seed(client, faults)
            store.prepare()
            client.identity = Account("new-guest")
            store.confirm("new-guest")
            faults.afterCommit = true
            assertTrue(runCatching { store.activate("new-guest") }.isFailure)
            val fields = disk.dataStore.data.first().asMap().mapKeys { it.key.name }
            assertFalse(fields.keys.any { it.startsWith("guest_migration/") || it.startsWith("local-guest/") })
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            // Even a same-process confirmation retry after an acknowledged-late commit is harmless.
            reopened.confirm("new-guest")
            assertTrue(repository(client, this, reopened).refreshProfile().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun pendingMigrationRejectsUnrelatedUidEvenWhenItIsAnonymous() =
        runBlocking {
            val client = Client()
            val store = seed(client)
            store.prepare()
            client.identity = Account("intended")
            store.confirm("intended")
            for (identity in listOf(Account("unrelated"), Account("registered", "a@b.co"))) {
                client.identity = identity
                val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
                val repo = repository(client, this, reopened)
                assertTrue(repo.refreshProfile().isFailure)
                assertFalse(repo.account.value.storageReady)
                assertEquals(PlayerProgress(), ProgressDataSource(reopened).progress.first())
                sourceIsIntact()
            }
            client.identity = Account("intended")
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            assertTrue(repository(client, this, reopened).refreshProfile().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun registeredSourceCannotBePreparedOrImportedIntoGuest() =
        runBlocking {
            val client = Client(Account("registered", "a@b.co"))
            val store = AccountDataStore(disk.dataStore) { client.currentUserId }
            store.activate("registered")
            ProgressDataSource(store).mergeIn(saved)
            assertTrue(runCatching { store.prepare() }.isFailure)
            val repo = repository(client, this, store)
            assertTrue(repo.logout().isSuccess)
            assertTrue(repo.ensureSignedIn().isSuccess)
            assertEquals(PlayerProgress(), ProgressDataSource(store).progress.first())
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            val restored = repository(client, this, reopened)
            assertTrue(restored.login("a@b.co", "123456").isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun restartWithIntentButNoFirebaseUidCanSafelyRetryCreation() =
        runBlocking {
            val client = Client()
            seed(client).prepare()
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            assertTrue(repository(client, this, reopened).ensureSignedIn().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
            assertEquals(1, client.creations)
        }

    @Test fun emptyInstallWithoutPendingIntentCreatesIsolatedGuest() =
        runBlocking {
            val client = Client()
            val store = AccountDataStore(disk.reopen()) { client.currentUserId }
            val repo = repository(client, this, store)
            assertTrue(repo.ensureSignedIn().isSuccess)
            assertTrue(repo.account.value.storageReady)
            assertEquals(PlayerProgress(), ProgressDataSource(store).progress.first())
        }

    @Test fun preflightDiskFailureNeverCallsFirebase() =
        runBlocking {
            val client = Client()
            val faults = FaultStore(disk.dataStore)
            val store = seed(client, faults)
            faults.beforeCommit = true
            assertTrue(repository(client, this, store).ensureSignedIn().isFailure)
            assertEquals(0, client.creations)
            assertEquals(null, client.currentUserId)
            sourceIsIntact()
        }

    @Test fun confirmedDirectRegistrationRecoversAfterRestartAndGuestLinkKeepsUid() =
        runBlocking {
            val client = Client()
            val store = seed(client)
            val first =
                AuthRepositoryImpl(client, this, guestMigration = store, activate = { uid, migrate ->
                    if (uid != null) throw IOException("interrupted before local migration")
                    store.activate(uid, migrate)
                })
            assertTrue(first.register("Name", "a@b.co", "123456", "123456").isFailure)
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            assertTrue(repository(client, this, reopened).refreshProfile().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
            val repo = repository(client, this, reopened)
            assertTrue(repo.logout().isSuccess)
            assertTrue(repo.ensureSignedIn().isSuccess)
            val guestUid = client.currentUserId
            ProgressDataSource(reopened).mergeIn(saved)
            assertTrue(repo.register("Name", "b@b.co", "123456", "123456").isSuccess)
            assertEquals(guestUid, client.currentUserId)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun preparedOfflineGuestCanLoginWithoutImportingItsProgress() =
        runBlocking {
            val client = Client()
            val store = seed(client)
            store.prepare()
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            val repo = repository(client, this, reopened)
            assertTrue(repo.login("a@b.co", "123456").isSuccess)
            assertEquals(PlayerProgress(), ProgressDataSource(reopened).progress.first())
            sourceIsIntact()
            assertTrue(repo.logout().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun confirmationRejectsUidOtherThanCurrentFirebaseIdentity() =
        runBlocking {
            val client = Client()
            val store = seed(client)
            store.prepare()
            client.identity = Account("intended")
            assertTrue(runCatching { store.confirm("unrelated") }.isFailure)
            sourceIsIntact()
            store.confirm("intended")
            val reopened = AccountDataStore(disk.reopen()) { client.currentUserId }
            assertTrue(repository(client, this, reopened).refreshProfile().isSuccess)
            assertEquals(saved, ProgressDataSource(reopened).progress.first())
        }

    @Test fun sameProcessCanRetryFailedUidConfirmationWithoutCreatingAnotherIdentity() =
        runBlocking {
            val client = Client()
            val faults = FaultStore(disk.dataStore)
            val store = seed(client, faults)
            client.afterFirebasePersisted = { faults.beforeCommit = true }
            val repo = repository(client, this, store)
            assertTrue(repo.ensureSignedIn().isFailure)
            sourceIsIntact()
            faults.beforeCommit = false
            assertTrue(repo.refreshProfile().isSuccess)
            assertEquals(saved, ProgressDataSource(store).progress.first())
            assertEquals(1, client.creations)
        }
}
