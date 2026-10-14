package com.comp90018.deadline.app

import android.content.Context
import android.util.Log
import com.comp90018.deadline.core.audio.AndroidAudioPlayer
import com.comp90018.deadline.core.audio.GameAudioManager
import com.comp90018.deadline.data.local.datastore.AccountDataStore
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.data.remote.firebase.FirebaseAuthDataSource
import com.comp90018.deadline.data.remote.firebase.FirebaseRemoteConfigDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreLeaderboardDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProfileDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreProgressDataSource
import com.comp90018.deadline.data.remote.firebase.FirestoreTransferDataSource
import com.comp90018.deadline.data.remote.firebase.RemoteConfigSource
import com.comp90018.deadline.data.repository.AuthRepositoryImpl
import com.comp90018.deadline.data.repository.ConfigRepositoryImpl
import com.comp90018.deadline.data.repository.LeaderboardRepositoryImpl
import com.comp90018.deadline.data.repository.ProgressRepositoryImpl
import com.comp90018.deadline.data.repository.SessionGate
import com.comp90018.deadline.data.repository.SettingsRepositoryImpl
import com.comp90018.deadline.data.sync.NetworkWatcher
import com.comp90018.deadline.data.sync.ProgressSyncManager
import com.comp90018.deadline.data.sync.ProgressTransferService
import com.comp90018.deadline.domain.level.generator.GeneratedLevelSource
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.CreateCodeResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.progress.ProgressTransfer
import com.comp90018.deadline.domain.progress.RedeemResult
import com.comp90018.deadline.domain.repository.AccountRepository
import com.comp90018.deadline.domain.repository.ConfigRepository
import com.comp90018.deadline.domain.repository.LeaderboardRepository
import com.comp90018.deadline.domain.repository.ProgressRepository
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Application-level dependency container; one instance lives in [DeadlineApp]. */
class AppContainer(
    context: Context,
    remoteConfigSource: RemoteConfigSource? = null,
) {
    private val sessionGate = SessionGate()
    private val firebaseAuth = FirebaseAuthDataSource(FirebaseAuth.getInstance())
    private val dataStore = AccountDataStore(DeadlineDataStore.create(context)) { firebaseAuth.currentUserId }

    val progressRepository: ProgressRepository = ProgressRepositoryImpl(ProgressDataSource(dataStore))

    private val localSettings = SettingsRepositoryImpl(SettingsDataSource(dataStore))
    val settingsRepository: SettingsRepository =
        object : SettingsRepository {
            override val settings = localSettings.settings

            override suspend fun update(
                transform: (com.comp90018.deadline.domain.settings.PlayerSettings) -> com.comp90018.deadline.domain.settings.PlayerSettings,
            ) {
                val owner = firebaseAuth.currentUserId
                sessionGate.run {
                    check(owner == firebaseAuth.currentUserId) { "Account changed; retry your settings change." }
                    localSettings.update(transform)
                }
            }
        }

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
            val version = dataStore.sessionVersion
            applicationScope.launch {
                sessionGate.run {
                    // Acquiring the first guest UID is the same player; login/logout starts a new session.
                    if (version != dataStore.sessionVersion) return@run
                    runCatching { progressRepository.recordCompletion(result) }
                        .onFailure {
                            Log.w(TAG, "Could not save completion of ${result.levelId}", it)
                            return@run
                        }
                    val nickname = settingsRepository.settings.first().nickname ?: DEFAULT_NICKNAME
                    val submitted = leaderboardRepository.submit(result, nickname)
                    Log.d(TAG, "Leaderboard submission for ${result.levelId}: $submitted")
                    syncProgress("win")
                }
            }
        }

    /** Latest saved progress, for callers that must read it synchronously (e.g. at a win). */
    val currentProgress: StateFlow<PlayerProgress> =
        progressRepository.progress.stateIn(applicationScope, SharingStarted.Eagerly, PlayerProgress())

    val authRepository: AccountRepository =
        AuthRepositoryImpl(
            firebaseAuth,
            applicationScope,
            gate = sessionGate,
            guestMigration = dataStore,
            activate = { uid, migrate -> dataStore.activate(uid, migrate) },
            profiles = FirestoreProfileDataSource(FirebaseFirestore.getInstance()),
            saveNickname = { nickname -> settingsRepository.update { it.copy(nickname = nickname) } },
            readNickname = { settingsRepository.settings.first().nickname },
        )

    val leaderboardRepository: LeaderboardRepository =
        LeaderboardRepositoryImpl(FirestoreLeaderboardDataSource(FirebaseFirestore.getInstance()), authRepository)

    private val progressSync =
        ProgressSyncManager(progressRepository, FirestoreProgressDataSource(FirebaseFirestore.getInstance()), authRepository)

    /** Moves progress to another phone with a transfer code (Settings). */
    private val transferService =
        ProgressTransferService(
            progressRepository,
            FirestoreTransferDataSource(FirebaseFirestore.getInstance()),
            authRepository,
            progressSync::sync,
        )

    val progressTransfer: ProgressTransfer =
        object : ProgressTransfer {
            override suspend fun createCode(): CreateCodeResult {
                val uid = authRepository.ensureSignedIn().getOrElse { return CreateCodeResult.Offline }
                return sessionGate.forUser(uid, { firebaseAuth.currentUserId }, { CreateCodeResult.Failed }) {
                    transferService.createCode()
                }
            }

            override suspend fun redeem(input: String): RedeemResult {
                val uid = authRepository.ensureSignedIn().getOrElse { return RedeemResult.Offline }
                return sessionGate.forUser(uid, { firebaseAuth.currentUserId }, { RedeemResult.Failed }) {
                    transferService.redeem(input)
                }
            }
        }

    private val networkWatcher = NetworkWatcher(context)

    /** Syncs progress whenever the device is or becomes online (#39). */
    fun startProgressSync() {
        // This observer intentionally lasts for the Application's entire process lifetime.
        networkWatcher.start { syncProgress("network available") }
    }

    /** Merges local progress with its cloud copy in the background; failures only wait for the next sync. */
    private fun syncProgress(reason: String) {
        applicationScope.launch {
            authRepository.ensureSignedIn()
            sessionGate.run { Log.d(TAG, "Progress sync ($reason): ${progressSync.sync()}") }
        }
    }

    suspend fun syncAccountProgress() = sessionGate.run { progressSync.sync() }

    /** Gameplay balance from Remote Config, or the compiled-in defaults (#40, #52). */
    val configRepository: ConfigRepository =
        ConfigRepositoryImpl(
            remoteConfigSource ?: FirebaseRemoteConfigDataSource(FirebaseRemoteConfig.getInstance()),
            onRejected = { Log.w(TAG, "Remote Config values refused, using defaults for: $it") },
        )

    /** Applies balance values fetched earlier, then fetches new ones; nothing waits for it. */
    fun refreshConfigInBackground() {
        applicationScope.launch { Log.d(TAG, "Game balance from ${configRepository.refresh()}") }
    }

    /** Obtains the anonymous identity without blocking anything; offline just means "not yet". */
    fun signInInBackground() {
        applicationScope.launch {
            authRepository.ensureSignedIn()
                .onSuccess { authRepository.refreshProfile() }
                .onFailure { Log.w(TAG, "Anonymous sign-in failed; will retry when needed", it) }
        }
    }

    private companion object {
        const val TAG = "AppContainer"

        /** Leaderboard name for players who have not chosen a nickname in Settings. */
        const val DEFAULT_NICKNAME = "Anonymous"
    }
}
