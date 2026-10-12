package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.remote.firebase.AccountAuthClient
import com.comp90018.deadline.data.remote.firebase.AnonymousAuthClient
import com.comp90018.deadline.data.remote.firebase.ProfileStore
import com.comp90018.deadline.domain.repository.Account
import com.comp90018.deadline.domain.repository.AccountRepository
import com.comp90018.deadline.domain.repository.AccountValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Anonymous identity with no sign-up step. An existing user is reused. Only one Firebase
 * sign-in runs at a time: it is started in [scope] and kept until Firebase finishes it, and
 * every caller that arrives meanwhile waits on that same operation. [timeoutMillis] limits
 * each caller's wait, not the operation, so a retry after a timeout joins the sign-in that is
 * still running instead of starting a second one (which could replace the identity when the
 * first completes late). Failures (offline, timeout, server error) come back as
 * [Result.failure]; cancelling a caller stops only that caller's wait and is rethrown to it.
 */
class AuthRepositoryImpl(
    private val client: AnonymousAuthClient,
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val gate: SessionGate = SessionGate(),
    private val activate: suspend (String?, Boolean) -> Unit = { _, _ -> },
    private val profiles: ProfileStore? = null,
    private val saveNickname: suspend (String) -> Unit = {},
    private val readNickname: suspend () -> String? = { null },
) : AccountRepository {
    private val operationLock = Mutex()
    private var currentOperation: Deferred<Result<String>>? = null

    // Only a newly created identity may adopt offline guest data. Retained across local-write retries.
    private var pendingGuestUid: String? = null

    private val mutableAccount = MutableStateFlow(snapshot())
    override val account: StateFlow<Account> = mutableAccount
    override val userId: Flow<String?> = account.map { it.uid }

    private fun snapshot(): Account = (client as? AccountAuthClient)?.currentAccount ?: Account(client.currentUserId)

    private suspend fun publish() {
        val next = snapshot()
        val previous = account.value
        mutableAccount.value = next.copy(nickname = previous.nickname.takeIf { next.uid == previous.uid }, storageReady = false)
        localWork {
            activate(next.uid, next.uid != null && next.uid == pendingGuestUid)
            pendingGuestUid = null
            mutableAccount.value =
                next.copy(
                    nickname = readNickname() ?: previous.nickname.takeIf { next.uid == previous.uid },
                )
        }
    }

    override suspend fun ensureSignedIn(): Result<String> {
        if (client.currentUserId != null) {
            return gate.run {
                attempt {
                    publish()
                    checkNotNull(client.currentUserId)
                }
            }
        }
        if (gate.isHeld()) return Result.failure(java.io.IOException("Guest is offline."))
        val operation =
            operationLock.withLock {
                currentOperation?.takeIf { it.isActive }
                    ?: scope.async { signInOnce() }.also { currentOperation = it }
            }
        return try {
            withTimeout(timeoutMillis) { operation.await() }
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(timeout)
        }
    }

    /**
     * The shared operation. It keeps its failure as a result instead of throwing, so a failed
     * sign-in reaches every waiting caller and never fails [scope] or the work running in it.
     */
    private suspend fun signInOnce(): Result<String> =
        gate.run {
            attempt {
                val uid = client.currentUserId ?: client.signInAnonymously().also { pendingGuestUid = it }
                publish()
                uid
            }
        }

    override suspend fun register(
        nickname: String,
        email: String,
        password: String,
        confirmation: String,
    ): Result<Unit> {
        AccountValidation.registration(nickname, email, password, confirmation)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        return gate.run {
            attempt {
                val accounts = checkNotNull(client as? AccountAuthClient)
                val before = accounts.currentAccount
                check(!before.registered) { "Already registered." }
                publish()
                if (before.uid != null) {
                    val linked = accounts.link(email.trim(), password)
                    check(linked.uid == before.uid) { "Account identity changed unexpectedly." }
                } else {
                    pendingGuestUid = accounts.create(email.trim(), password).uid
                }
                completeAuthentication {
                    publish()
                    // Persist the chosen name before the remote profile write, so offline retries retain it.
                    localWork { saveNickname(nickname.trim()) }
                    mutableAccount.value = account.value.copy(nickname = nickname.trim())
                    writeProfile(nickname.trim())
                }
            }
        }
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Result<Unit> {
        AccountValidation.login(email, password)?.let { return Result.failure(IllegalArgumentException(it)) }
        return gate.run {
            attempt {
                publish()
                checkNotNull(client as? AccountAuthClient).login(email.trim(), password)
                completeAuthentication {
                    publish() // Never migrate another UID's progress on login.
                    writeProfile()
                }
            }
        }
    }

    override suspend fun logout(): Result<Unit> =
        gate.run {
            attempt {
                try {
                    // Isolate first. If this fails, Firebase remains signed in and recovery reselects that UID.
                    localWork { activate(null, false) }
                    checkNotNull(client as? AccountAuthClient).signOut()
                    publish()
                } catch (error: Exception) {
                    mutableAccount.value = snapshot().copy(storageReady = false)
                    throw error
                }
            }
        }

    override suspend fun updateNickname(nickname: String): Result<Unit> {
        AccountValidation.nickname(nickname)?.let { return Result.failure(IllegalArgumentException(it)) }
        return gate.run {
            attempt {
                publish()
                check(account.value.registered) { "Log in to edit your profile." }
                writeProfile(nickname.trim(), recordLogin = false)
            }
        }
    }

    override suspend fun refreshProfile(): Result<Unit> =
        gate.run {
            attempt {
                publish()
                if (account.value.registered) writeProfile()
            }
        }

    private suspend fun writeProfile(
        nickname: String? = null,
        recordLogin: Boolean = true,
    ) {
        val store = profiles ?: return
        val name =
            try {
                withTimeout(15_000) { store.loadOrCreate(account.value, nickname, recordLogin) }
            } catch (error: TimeoutCancellationException) {
                throw ProfileUnavailableException()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                throw ProfileUnavailableException()
            }
        localWork { saveNickname(name) }
        mutableAccount.value = account.value.copy(nickname = name)
    }

    private suspend fun <T> localWork(block: suspend () -> T): T =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableAccount.value = snapshot().copy(storageReady = false)
            throw AccountStorageUnavailableException()
        }

    private suspend fun completeAuthentication(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: AccountStorageUnavailableException) {
            throw AccountStorageUnavailableException(authenticationSucceeded = true)
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}

class ProfileUnavailableException : Exception("You are signed in. Profile sync is unavailable; retry from Account when online.")

class AccountStorageUnavailableException(val authenticationSucceeded: Boolean = false) :
    Exception("Account storage is unavailable. Retry account setup; do not register again.")
