package com.comp90018.deadline.app

import android.content.Context
import android.util.Log
import com.comp90018.deadline.core.audio.AndroidAudioPlayer
import com.comp90018.deadline.core.audio.GameAudioManager
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreLeaderboardDataSource
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.repository.LeaderboardRepositoryImpl
import com.comp90018.deadline.data.repository.ProgressRepositoryImpl
import com.comp90018.deadline.data.repository.SettingsRepositoryImpl
import com.comp90018.deadline.domain.level.generator.GeneratedLevelSource
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.AuthRepository
import com.comp90018.deadline.domain.repository.LeaderboardRepository
import com.comp90018.deadline.domain.repository.ProgressRepository
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Application-level dependency container; one instance lives in [DeadlineApp]. */
class AppContainer(context: Context) {
    private val dataStore = DeadlineDataStore.create(context)

    val progressRepository: ProgressRepository = ProgressRepositoryImpl(ProgressDataSource(dataStore))

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(SettingsDataSource(dataStore))

    /** Generates a new board each time a level starts. */
    val levelSource: LevelSource = GeneratedLevelSource()

    /** Work that must finish even after the screen that started it is gone. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val audioScope = CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate)

    val gameAudioManager = GameAudioManager(AndroidAudioPlayer(context, audioScope), audioScope)

    init {
        applicationScope.launch(Dispatchers.Main.immediate) {
            settingsRepository.settings.collect { gameAudioManager.updateSettings(it) }
        }
    }

    /**
     * Saves each win locally, then submits it to the online leaderboard under the player's
     * nickname. A failed save or submission costs one record, never the game, so errors are
     * logged and not rethrown; an offline submission stays queued on the device.
     */
    val completionRecorder =
        CompletionRecorder { result ->
            applicationScope.launch {
                runCatching { progressRepository.recordCompletion(result) }
                    .onFailure { Log.w(TAG, "Could not save completion of ${result.levelId}", it) }
                val nickname = settingsRepository.settings.first().nickname ?: DEFAULT_NICKNAME
                val submitted = leaderboardRepository.submit(result, nickname)
                Log.d(TAG, "Leaderboard submission for ${result.levelId}: $submitted")
            }
        }

    /** Latest saved progress, for callers that must read it synchronously (e.g. at a win). */
    val currentProgress: StateFlow<PlayerProgress> =
        progressRepository.progress.stateIn(applicationScope, SharingStarted.Eagerly, PlayerProgress())

    val authRepository: AuthRepository =
        AuthRepositoryImpl(FirebaseAuthDataSource(FirebaseAuth.getInstance()), applicationScope)

    val leaderboardRepository: LeaderboardRepository =
        LeaderboardRepositoryImpl(FirestoreLeaderboardDataSource(FirebaseFirestore.getInstance()), authRepository)

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

        /** Leaderboard name for players who have not chosen a nickname in Settings. */
        const val DEFAULT_NICKNAME = "Anonymous"
    }
}
