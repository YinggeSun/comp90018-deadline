package com.comp90018.deadline.feature.account

import com.comp90018.deadline.data.repository.AccountStorageUnavailableException
import com.comp90018.deadline.data.repository.ProfileUnavailableException
import com.comp90018.deadline.data.sync.SyncResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.Account
import com.comp90018.deadline.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private class Repository : AccountRepository {
        override val account = MutableStateFlow(Account())
        override val userId = account.map { it.uid }
        var calls = 0
        val result = CompletableDeferred<Result<Unit>>()

        override suspend fun ensureSignedIn() = Result.success("guest")

        override suspend fun register(
            nickname: String,
            email: String,
            password: String,
            confirmation: String,
        ): Result<Unit> {
            calls++
            return result.await()
        }

        override suspend fun login(
            email: String,
            password: String,
        ) = result.await()

        override suspend fun logout() = Result.success(Unit)

        override suspend fun updateNickname(nickname: String) = Result.success(Unit)

        override suspend fun refreshProfile() = Result.success(Unit)
    }

    @Test fun guestIsAvailableBeforeAnyAccountOperation() =
        runTest {
            val repo = Repository()
            val vm = AccountViewModel(repo, backgroundScope) { SyncResult.Synced(PlayerProgress()) }
            assertFalse(vm.account.value.registered)
            assertFalse(vm.action.value.busy)
            assertEquals(0, repo.calls)
        }

    @Test fun duplicateSubmissionIsIgnoredAndSuccessSignalsNavigationAfterSync() =
        runTest {
            val repo = Repository()
            var syncs = 0
            val vm =
                AccountViewModel(repo, backgroundScope) {
                    syncs++
                    SyncResult.Synced(PlayerProgress())
                }
            vm.register("Name", "a@b.co", "123456", "123456")
            vm.register("Name", "a@b.co", "123456", "123456")
            runCurrent()
            assertTrue(vm.action.value.busy)
            assertEquals(1, repo.calls)
            repo.result.complete(Result.success(Unit))
            runCurrent()
            assertTrue(vm.action.value.succeeded)
            assertFalse(vm.action.value.busy)
            assertEquals(1, syncs)
        }

    @Test fun failureStaysOnFormAndDoesNotSync() =
        runTest {
            val repo = Repository()
            var syncs = 0
            val vm =
                AccountViewModel(repo, backgroundScope) {
                    syncs++
                    SyncResult.Synced(PlayerProgress())
                }
            vm.login("a@b.co", "incorrect")
            repo.result.complete(Result.failure(IOException()))
            runCurrent()
            assertNotNull(vm.action.value.error)
            assertFalse(vm.action.value.succeeded)
            assertFalse(vm.action.value.busy)
            assertEquals(0, syncs)
        }

    @Test fun successfulLoginSurvivesSyncException() =
        runTest {
            val repo = Repository()
            val vm = AccountViewModel(repo, backgroundScope) { throw IOException("sync failed") }
            vm.login("a@b.co", "123456")
            repo.result.complete(Result.success(Unit))
            runCurrent()
            assertTrue(vm.action.value.succeeded)
            assertTrue(vm.action.value.progressPending)
            assertFalse(vm.action.value.busy)
            assertEquals(null, vm.action.value.error)
        }

    @Test fun successfulLoginSurvivesSyncCancellation() =
        runTest {
            val repo = Repository()
            val vm = AccountViewModel(repo, backgroundScope) { throw CancellationException("sync cancelled") }
            vm.login("a@b.co", "123456")
            repo.result.complete(Result.success(Unit))
            runCurrent()
            assertTrue(vm.action.value.succeeded)
            assertTrue(vm.action.value.progressPending)
            assertFalse(vm.action.value.syncing)
            assertEquals(null, vm.action.value.error)
        }

    @Test fun profilePendingIsAuthenticationSuccessWithoutMisleadingNavigation() =
        runTest {
            val repo = Repository()
            val vm = AccountViewModel(repo, backgroundScope) { SyncResult.Offline }
            vm.register("Name", "a@b.co", "123456", "123456")
            repo.result.complete(Result.failure(ProfileUnavailableException()))
            runCurrent()
            assertTrue(vm.action.value.succeeded)
            assertTrue(vm.action.value.profilePending)
            assertTrue(vm.action.value.progressPending)
            assertFalse(vm.action.value.canNavigate)
            assertEquals(null, vm.action.value.error)
        }

    @Test fun localSetupFailureAfterAuthIsNotOfferedAsRegistrationRetry() =
        runTest {
            val repo = Repository()
            var syncs = 0
            val vm =
                AccountViewModel(repo, backgroundScope) {
                    syncs++
                    SyncResult.Failed
                }
            vm.register("Name", "a@b.co", "123456", "123456")
            repo.result.complete(Result.failure(AccountStorageUnavailableException(authenticationSucceeded = true)))
            runCurrent()
            assertTrue(vm.action.value.succeeded)
            assertTrue(vm.action.value.localPending)
            assertEquals(0, syncs)
        }

    @Test fun gameplayIsNotBlockedWhileProgressSyncIsPending() =
        runTest {
            val repo = Repository()
            val release = CompletableDeferred<Unit>()
            val vm =
                AccountViewModel(repo, backgroundScope) {
                    release.await()
                    SyncResult.Synced(PlayerProgress())
                }
            vm.register("Name", "a@b.co", "123456", "123456")
            repo.result.complete(Result.success(Unit))
            runCurrent()
            assertFalse(vm.action.value.busy)
            assertTrue(vm.action.value.syncing)
            vm.register("Name", "a@b.co", "123456", "123456")
            assertEquals(1, repo.calls)
            release.complete(Unit)
            runCurrent()
            assertTrue(vm.action.value.canNavigate)
        }
}
