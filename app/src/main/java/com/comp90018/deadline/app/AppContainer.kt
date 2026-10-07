package com.comp90018.deadline.app

import android.content.Context
import android.util.Log
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.data.repository.ProgressRepositoryImpl
import com.comp90018.deadline.data.repository.SettingsRepositoryImpl
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.ProgressRepository
import com.comp90018.deadline.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Application-level dependency container; one instance lives in [DeadlineApp]. */
class AppContainer(context: Context) {
    private val dataStore = DeadlineDataStore.create(context)

    val progressRepository: ProgressRepository = ProgressRepositoryImpl(ProgressDataSource(dataStore))

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(SettingsDataSource(dataStore))

    /** Work that must finish even after the screen that started it is gone. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A failed save costs one record, never the game, so errors are logged and not rethrown. */
    val completionRecorder = CompletionRecorder { result ->
        applicationScope.launch {
            runCatching { progressRepository.recordCompletion(result) }
                .onFailure { Log.w(TAG, "Could not save completion of ${result.levelId}", it) }
        }
    }

    /** Latest saved progress, for callers that must read it synchronously (e.g. at a win). */
    val currentProgress: StateFlow<PlayerProgress> =
        progressRepository.progress.stateIn(applicationScope, SharingStarted.Eagerly, PlayerProgress())

    private companion object {
        const val TAG = "AppContainer"
    }
}
