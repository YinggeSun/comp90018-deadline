package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.local.DataStoreTestRule
import com.comp90018.deadline.data.local.datastore.AccountDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.data.remote.firebase.AccountAuthClient
import com.comp90018.deadline.data.remote.firebase.ProfileStore
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.Account
import com.comp90018.deadline.domain.repository.AccountValidation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryTest {
    @get:Rule val disk = DataStoreTestRule()

    private class Client(initial: Account = Account("guest")) : AccountAuthClient {
        val state = MutableStateFlow(initial)
        var links = 0
        var creates = 0
        var anonymous = 0
        var fail = false
        override val currentAccount get() = state.value
        override val currentUserId get() = currentAccount.uid

        override fun userIdChanges() = state.map { it.uid }

        override suspend fun signInAnonymously(): String {
            anonymous++
            if (fail) throw IOException()
            state.value = Account("new-guest")
            return "new-guest"
        }

        override suspend fun link(
            email: String,
            password: String,
        ): Account {
            links++
            if (fail) throw IOException()
            return currentAccount.copy(email = email).also { state.value = it }
        }

        override suspend fun create(
            email: String,
            password: String,
        ): Account {
            creates++
            if (fail) throw IOException()
            return Account("created", email).also { state.value = it }
        }

        override suspend fun login(
            email: String,
            password: String,
        ): Account {
            if (fail) throw IOException()
            return Account("registered", email).also { state.value = it }
        }

        override fun signOut() {
            state.value = Account()
        }
    }

    private class Profiles : ProfileStore {
        val names = mutableMapOf<String, String>()
        var fail = false

        override suspend fun loadOrCreate(
            account: Account,
            nickname: String?,
            recordLogin: Boolean,
        ): String {
            if (fail) throw IOException()
            val uid = checkNotNull(account.uid)
            return (nickname ?: names[uid] ?: "Player").also { names[uid] = it }
        }
    }

    @Test fun validatesRegistrationBeforeCallingFirebase() =
        runBlocking {
            val client = Client()
            val repo = AuthRepositoryImpl(client, this)
            assertTrue(repo.register("", "bad", "", "").isFailure)
            assertNotNull(AccountValidation.registration("Name", "bad", "123456", "123456"))
            assertNotNull(AccountValidation.registration("Name", "a@b.co", "123", "123"))
            assertNotNull(AccountValidation.registration("Name", "a@b.co", "123456", "abcdef"))
            assertNull(AccountValidation.registration("Name", "a@b.co", "123456", "123456"))
            assertEquals(0, client.links)
            assertEquals(0, client.creates)
        }

    @Test fun linksGuestPreservingUidProgressNicknameAndDeviceSettings() =
        runBlocking {
            val client = Client()
            val store = AccountDataStore(disk.dataStore)
            val progress = ProgressDataSource(store)
            val settings = SettingsDataSource(store)
            // Pre-upgrade legacy storage is adopted without dropping progress.
            progress.mergeIn(PlayerProgress(setOf("level_1"), 3))
            settings.update { it.copy(soundEffectsEnabled = false) }
            val repo =
                AuthRepositoryImpl(
                    client,
                    this,
                    activate = { id, migrate -> store.activate(id, migrate) },
                    profiles = Profiles(),
                    saveNickname = { name -> settings.update { it.copy(nickname = name) } },
                )
            assertTrue(repo.register("Ada", "a@b.co", "123456", "123456").isSuccess)
            assertEquals("guest", repo.account.value.uid)
            assertTrue(repo.account.value.registered)
            assertEquals(1, client.links)
            assertEquals(0, client.creates)
            assertEquals(setOf("level_1"), progress.progress.first().completedLevelIds)
            assertEquals("Ada", settings.settings.first().nickname)
            assertFalse(settings.settings.first().soundEffectsEnabled)
        }

    @Test fun failedLinkDoesNotCreateOrSwitchAccounts() =
        runBlocking {
            val client = Client().apply { fail = true }
            val repo = AuthRepositoryImpl(client, this)
            assertTrue(repo.register("Ada", "a@b.co", "123456", "123456").isFailure)
            assertEquals("guest", repo.account.value.uid)
            assertFalse(repo.account.value.registered)
            assertEquals(0, client.creates)
        }

    @Test fun offlineLocalPlayerCanRegisterWithoutAnAnonymousUid() =
        runBlocking {
            val client = Client(Account())
            val store = AccountDataStore(disk.dataStore)
            store.activate(null)
            val progress = ProgressDataSource(store)
            progress.mergeIn(PlayerProgress(setOf("level_2"), 5))
            val repo = AuthRepositoryImpl(client, this, activate = { id, migrate -> store.activate(id, migrate) })
            assertTrue(repo.register("Ada", "a@b.co", "123456", "123456").isSuccess)
            assertEquals(1, client.creates)
            assertEquals(setOf("level_2"), progress.progress.first().completedLevelIds)
        }

    @Test fun loginLoadsOnlyTargetProfileAndProgressAndLogoutHidesThemEvenOffline() =
        runBlocking {
            val client = Client()
            val store = AccountDataStore(disk.dataStore)
            val progress = ProgressDataSource(store)
            val settings = SettingsDataSource(store)
            store.activate("registered")
            progress.mergeIn(PlayerProgress(setOf("level_3"), 7))
            store.activate("guest")
            progress.mergeIn(PlayerProgress(setOf("level_1"), 3))
            settings.update { it.copy(nickname = "Guest name") }
            val profiles = Profiles().apply { names["registered"] = "Returning player" }
            val repo =
                AuthRepositoryImpl(
                    client,
                    this,
                    activate = { id, migrate -> store.activate(id, migrate) },
                    profiles = profiles,
                    saveNickname = { name -> settings.update { it.copy(nickname = name) } },
                )
            assertTrue(repo.login("a@b.co", "123456").isSuccess)
            assertEquals(setOf("level_3"), progress.progress.first().completedLevelIds)
            assertEquals("Returning player", repo.account.value.nickname)
            assertTrue(repo.updateNickname("New name").isSuccess)
            assertEquals("New name", profiles.names["registered"])
            assertTrue(repo.logout().isSuccess)
            client.fail = true
            assertTrue(repo.ensureSignedIn().isFailure)
            assertEquals(PlayerProgress(), progress.progress.first())
            assertNull(settings.settings.first().nickname)
            assertFalse(repo.account.value.registered)
            client.fail = false
            assertTrue(repo.ensureSignedIn().isSuccess)
            assertEquals("new-guest", repo.account.value.uid)
            assertEquals(PlayerProgress(), progress.progress.first())
            assertTrue(repo.login("a@b.co", "123456").isSuccess)
            assertEquals(setOf("level_3"), progress.progress.first().completedLevelIds)
            store.activate("guest")
            assertEquals(setOf("level_1"), progress.progress.first().completedLevelIds)
        }

    @Test fun failedLoginPreservesGuestData() =
        runBlocking {
            val client = Client().apply { fail = true }
            val store = AccountDataStore(disk.dataStore)
            val progress = ProgressDataSource(store)
            progress.mergeIn(PlayerProgress(setOf("level_1"), 3))
            val repo = AuthRepositoryImpl(client, this, activate = { id, migrate -> store.activate(id, migrate) })
            assertTrue(repo.login("a@b.co", "123456").isFailure)
            assertEquals("guest", repo.account.value.uid)
            assertEquals(setOf("level_1"), progress.progress.first().completedLevelIds)
        }

    @Test fun profileFailureDoesNotUndoSuccessfulAuthentication() =
        runBlocking {
            val client = Client()
            val profiles = Profiles().apply { fail = true }
            val repo = AuthRepositoryImpl(client, this, profiles = profiles)
            assertTrue(repo.register("Ada", "a@b.co", "123456", "123456").exceptionOrNull() is ProfileUnavailableException)
            assertTrue(repo.account.value.registered)
            assertEquals("guest", repo.account.value.uid)
            profiles.fail = false
            assertTrue(repo.refreshProfile().isSuccess)
        }

    @Test fun accountSwitchWaitsForInFlightSyncAndAllowsNestedCalls() =
        runTest {
            val gate = SessionGate()
            val client = Client()
            val repo = AuthRepositoryImpl(client, this, gate = gate)
            val release = CompletableDeferred<Unit>()
            val sync =
                async {
                    gate.run {
                        gate.run { release.await() }
                        assertEquals("guest", client.currentUserId)
                    }
                }
            runCurrent()
            val login = async { repo.login("a@b.co", "123456") }
            runCurrent()
            assertFalse(login.isCompleted)
            release.complete(Unit)
            sync.await()
            assertTrue(login.await().isSuccess)
            assertEquals("registered", client.currentUserId)
        }

    @Test fun loginAuthSuccessWithDiskFailurePublishesActualUidWithoutOldProfile() =
        runBlocking {
            val client = Client()
            var failActivation = true
            var selected = "guest"
            val repo =
                AuthRepositoryImpl(
                    client,
                    this,
                    activate = { uid, _ ->
                        if (uid == "registered" && failActivation) throw IOException("disk full")
                        selected = uid.orEmpty()
                    },
                    readNickname = { if (selected == "guest") "Guest name" else "Registered name" },
                )
            repo.ensureSignedIn()
            val failure = repo.login("a@b.co", "123456").exceptionOrNull()
            assertTrue((failure as AccountStorageUnavailableException).authenticationSucceeded)
            assertEquals(client.currentUserId, repo.account.value.uid)
            assertTrue(repo.account.value.registered)
            assertFalse(repo.account.value.storageReady)
            assertNull(repo.account.value.nickname)
            failActivation = false
            assertTrue(repo.refreshProfile().isSuccess)
            assertEquals("Registered name", repo.account.value.nickname)
            assertTrue(repo.account.value.storageReady)
        }

    @Test fun newIdentityKeepsGuestMigrationIntentAcrossLocalFailureAndRetry() =
        runBlocking {
            val client = Client(Account())
            var failActivation = true
            val activations = mutableListOf<Pair<String?, Boolean>>()
            val repo =
                AuthRepositoryImpl(client, this, activate = { uid, migrate ->
                    activations += uid to migrate
                    if (uid == "created" && failActivation) throw IOException("disk full")
                })
            val error = repo.register("Name", "a@b.co", "123456", "123456").exceptionOrNull()
            assertTrue((error as AccountStorageUnavailableException).authenticationSucceeded)
            assertEquals("created", repo.account.value.uid)
            assertFalse(repo.account.value.storageReady)
            failActivation = false
            assertTrue(repo.refreshProfile().isSuccess)
            assertEquals("created" to true, activations.last())
            assertTrue(repo.refreshProfile().isSuccess)
            assertEquals("created" to false, activations.last())
            assertEquals(1, client.creates)
        }

    @Test fun nicknameDiskFailureAfterLinkDoesNotReportGuestOrRepeatLink() =
        runBlocking {
            val client = Client()
            var fail = true
            val repo = AuthRepositoryImpl(client, this, saveNickname = { if (fail) throw IOException() })
            val error = repo.register("Name", "a@b.co", "123456", "123456").exceptionOrNull()
            assertTrue((error as AccountStorageUnavailableException).authenticationSucceeded)
            assertEquals("guest", repo.account.value.uid)
            assertTrue(repo.account.value.registered)
            fail = false
            assertTrue(repo.refreshProfile().isSuccess)
            assertEquals(1, client.links)
        }

    @Test fun logoutDiskFailureLeavesActualRegisteredIdentityAndRecoverableStorage() =
        runBlocking {
            val client = Client(Account("registered", "a@b.co"))
            var fail = true
            val repo = AuthRepositoryImpl(client, this, activate = { uid, _ -> if (uid == null && fail) throw IOException() })
            assertTrue(repo.logout().isFailure)
            assertEquals("registered", client.currentUserId)
            assertEquals(client.currentUserId, repo.account.value.uid)
            assertTrue(repo.account.value.registered)
            assertFalse(repo.account.value.storageReady)
            fail = false
            assertTrue(repo.refreshProfile().isSuccess)
            assertTrue(repo.account.value.storageReady)
            assertTrue(repo.logout().isSuccess)
            assertNull(repo.account.value.uid)
        }

    @Test fun loginProfileFailureNeverKeepsGuestNickname() =
        runBlocking {
            val client = Client()
            val profiles = Profiles().apply { fail = true }
            val repo =
                AuthRepositoryImpl(
                    client,
                    this,
                    profiles = profiles,
                    readNickname = { if (client.currentUserId == "guest") "Guest private name" else null },
                )
            repo.ensureSignedIn()
            assertTrue(repo.login("a@b.co", "123456").exceptionOrNull() is ProfileUnavailableException)
            assertEquals("registered", repo.account.value.uid)
            assertNull(repo.account.value.nickname)
            assertTrue(repo.account.value.storageReady)
            profiles.fail = false
            profiles.names["registered"] = "Correct profile"
            assertTrue(repo.refreshProfile().isSuccess)
            assertEquals("Correct profile", repo.account.value.nickname)
        }

    @Test fun delayedAnonymousRequestDoesNotMigrateGuestIntoAnExistingLogin() =
        runTest {
            val gate = SessionGate()
            val client = Client(Account())
            val activations = mutableListOf<Pair<String?, Boolean>>()
            val repo =
                AuthRepositoryImpl(
                    client,
                    backgroundScope,
                    gate = gate,
                    activate = { uid, migrate -> activations += uid to migrate },
                )
            val release = CompletableDeferred<Unit>()
            val login =
                async {
                    gate.run {
                        release.await()
                        client.state.value = Account("existing", "a@b.co")
                    }
                }
            runCurrent()
            val guestRequest = async { repo.ensureSignedIn() }
            runCurrent()
            release.complete(Unit)
            login.await()
            assertEquals("existing", guestRequest.await().getOrThrow())
            assertEquals(0, client.anonymous)
            assertEquals("existing" to false, activations.last())
        }
}
