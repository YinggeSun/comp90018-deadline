package com.comp90018.deadline.app

import android.content.Context
import android.util.Log
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.repository.ProgressRepositoryImpl
import com.comp90018.deadline.data.repository.SettingsRepositoryImpl
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.repository.AuthRepository
import com.comp90018.deadline.domain.repository.ProgressRepository
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application-level dependency container; one instance lives in [DeadlineApp]. */
class AppContainer(context: Context) {
    private val dataStore = DeadlineDataStore.create(context)

    val progressRepository: ProgressRepository = ProgressRepositoryImpl(ProgressDataSource(dataStore))

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(SettingsDataSource(dataStore))

    /** Work that must finish even after the screen that started it is gone. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val authRepository: AuthRepository =
        AuthRepositoryImpl(FirebaseAuthDataSource(FirebaseAuth.getInstance()), applicationScope)

    /** A failed save costs one record, never the game, so errors are logged and not rethrown. */
    val completionRecorder = CompletionRecorder { result ->
        applicationScope.launch {
            runCatching { progressRepository.recordCompletion(result) }
                .onFailure { Log.w(TAG, "Could not save completion of ${result.levelId}", it) }
        }
    }

    /** Obtains the anonymous identity without blocking anything; offline just means "not yet". */
    fun signInInBackground() {
        applicationScope.launch {
            authRepository.ensureSignedIn()
                .onSuccess { Log.d(TAG, "Signed in anonymously") }
                .onFailure { Log.w(TAG, "Anonymous sign-in failed; will retry when needed", it) }
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
