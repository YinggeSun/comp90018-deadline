package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.data.config.RemoteBalance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/** Remote Config values; a seam for JVM tests. */
interface RemoteConfigSource {
    /**
     * The values published in Remote Config and activated on this device, now or in an earlier
     * session (Remote Config keeps them on disk). Keys that were never published are absent.
     */
    suspend fun activated(): Map<String, String>

    /** Fetches the latest published values and activates them. Throws on failure. */
    suspend fun fetchAndActivate()
}

/**
 * Firebase Remote Config for the [RemoteBalance] keys. The compiled-in defaults are registered
 * as Remote Config defaults, and new values are fetched at most once per
 * [minimumFetchIntervalSeconds], which Firebase also enforces.
 */
class FirebaseRemoteConfigDataSource(
    private val remoteConfig: FirebaseRemoteConfig,
    private val minimumFetchIntervalSeconds: Long = DEFAULT_MINIMUM_FETCH_INTERVAL_SECONDS,
) : RemoteConfigSource {
    private val setupLock = Mutex()
    private var isSetUp = false

    override suspend fun activated(): Map<String, String> {
        setUp()
        return RemoteBalance.KEYS
            .associateWith { remoteConfig.getValue(it) }
            .filterValues { it.source == FirebaseRemoteConfig.VALUE_SOURCE_REMOTE }
            .mapValues { (_, value) -> value.asString() }
    }

    override suspend fun fetchAndActivate() {
        setUp()
        remoteConfig.fetchAndActivate().await()
    }

    private suspend fun setUp() =
        setupLock.withLock {
            if (isSetUp) return@withLock
            remoteConfig
                .setConfigSettingsAsync(
                    FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(minimumFetchIntervalSeconds).build(),
                ).await()
            remoteConfig.setDefaultsAsync(RemoteBalance.defaults()).await()
            remoteConfig.ensureInitialized().await()
            isSetUp = true
        }

    private companion object {
        /** An hour, so balance changes reach playtesters quickly without exceeding Firebase's quota. */
        const val DEFAULT_MINIMUM_FETCH_INTERVAL_SECONDS = 60 * 60L
    }
}
