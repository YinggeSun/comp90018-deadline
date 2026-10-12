package com.comp90018.deadline.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.data.repository.AccountStorageUnavailableException
import com.comp90018.deadline.data.repository.ProfileUnavailableException
import com.comp90018.deadline.data.sync.SyncResult
import com.comp90018.deadline.domain.repository.AccountRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountActionState(
    val busy: Boolean = false,
    val error: String? = null,
    val succeeded: Boolean = false,
    val profilePending: Boolean = false,
    val progressPending: Boolean = false,
    val localPending: Boolean = false,
    val syncing: Boolean = false,
) {
    val canNavigate: Boolean get() = succeeded && !profilePending && !progressPending && !localPending && !syncing
}

/** Credentials only live in the form/call stack; never in SavedStateHandle or persistent storage. */
class AccountViewModel(
    private val repository: AccountRepository,
    private val workScope: CoroutineScope,
    private val sync: suspend () -> SyncResult,
) : ViewModel() {
    val account = repository.account
    private val mutableAction = MutableStateFlow(AccountActionState())
    val action = mutableAction.asStateFlow()

    fun register(
        nickname: String,
        email: String,
        password: String,
        confirmation: String,
    ) = submit(authenticating = true) { repository.register(nickname, email, password, confirmation) }

    fun login(
        email: String,
        password: String,
    ) = submit(authenticating = true) { repository.login(email, password) }

    fun nickname(value: String) = submit { repository.updateNickname(value) }

    fun refresh() = submit { repository.refreshProfile() }

    fun recoverLocalGuest() = submit { repository.recoverLocalGuest() }

    fun logout() =
        submit {
            repository.logout().also { if (it.isSuccess) repository.ensureSignedIn() }
        }

    private fun submit(
        authenticating: Boolean = false,
        operation: suspend () -> Result<Unit>,
    ) {
        if (mutableAction.value.busy || mutableAction.value.syncing) return
        mutableAction.value = AccountActionState(busy = true)
        // The app scope lets an SDK operation settle and select its local namespace even if
        // Android destroys this screen. Never cancel an in-flight account switch on navigation.
        workScope.launch {
            try {
                val result = operation()
                val failure = result.exceptionOrNull()
                val profilePending = failure is ProfileUnavailableException
                val localPending = failure is AccountStorageUnavailableException
                val authenticated =
                    authenticating &&
                        (profilePending || (failure as? AccountStorageUnavailableException)?.authenticationSucceeded == true)
                val succeeded = result.isSuccess || authenticated
                mutableAction.value =
                    AccountActionState(
                        error = failure?.takeUnless { authenticated || profilePending || localPending }?.let(::accountError),
                        succeeded = succeeded,
                        profilePending = profilePending,
                        localPending = localPending,
                        progressPending = succeeded,
                        syncing = succeeded && !localPending,
                    )
                if (succeeded && !localPending) {
                    try {
                        val synced = sync() is SyncResult.Synced
                        mutableAction.value = mutableAction.value.copy(progressPending = !synced, syncing = false)
                    } catch (cancelled: CancellationException) {
                        mutableAction.value = mutableAction.value.copy(progressPending = true, syncing = false)
                        throw cancelled
                    } catch (error: Exception) {
                        mutableAction.value = mutableAction.value.copy(progressPending = true, syncing = false)
                    }
                }
            } catch (cancelled: CancellationException) {
                mutableAction.value = mutableAction.value.copy(busy = false, syncing = false)
                throw cancelled
            } catch (error: Exception) {
                mutableAction.value = AccountActionState(error = accountError(error))
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    AccountViewModel(container.authRepository, container.applicationScope, container::syncAccountProgress)
                }
            }
    }
}

internal fun accountError(error: Throwable): String =
    when (error) {
        is ProfileUnavailableException -> checkNotNull(error.message)
        is IllegalArgumentException -> error.message ?: "Check the fields and try again."
        is FirebaseNetworkException -> "Cannot connect. Check your connection and try again. Your saved progress is safe."
        is FirebaseAuthException ->
            when (error.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
                    "This email already has an account. Your guest progress is unchanged. Log in, or use another email."
                "ERROR_WEAK_PASSWORD" -> "Choose a stronger password that meets the project's password policy."
                "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
                "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
                    "The email or password is incorrect."
                "ERROR_USER_DISABLED" -> "This account is disabled. Contact support."
                "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait and try again."
                "ERROR_OPERATION_NOT_ALLOWED" -> "Email/password sign-in is not enabled for this app yet."
                else -> "Account operation failed. Your saved progress is safe; please try again."
            }
        else -> "Account operation failed. Your saved progress is safe; please try again."
    }
