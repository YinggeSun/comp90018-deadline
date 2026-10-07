package com.comp90018.deadline.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Maps [PlayerSettings] to and from Preferences keys. Missing or invalid values read as defaults. */
class SettingsDataSource(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<PlayerSettings> =
        dataStore.safeData().map(::read).distinctUntilChanged()

    suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        dataStore.edit { preferences ->
            val updated = transform(read(preferences))
            if (updated.nickname == null) preferences.remove(NICKNAME)
            else preferences[NICKNAME] = updated.nickname
            preferences[HAPTICS] = updated.hapticsEnabled
            preferences[SHAKE] = updated.shakeToShuffleEnabled
            preferences[TILT] = updated.tiltToPeekEnabled
        }
    }

    private fun read(preferences: Preferences): PlayerSettings {
        val defaults = PlayerSettings()
        return PlayerSettings(
            nickname = preferences.typed(NICKNAME)?.takeIf {
                it.isNotBlank() && it.length <= PlayerSettings.MAX_NICKNAME_LENGTH
            },
            hapticsEnabled = preferences.typed(HAPTICS) ?: defaults.hapticsEnabled,
            shakeToShuffleEnabled = preferences.typed(SHAKE) ?: defaults.shakeToShuffleEnabled,
            tiltToPeekEnabled = preferences.typed(TILT) ?: defaults.tiltToPeekEnabled
        )
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("settings_nickname")
        val HAPTICS = booleanPreferencesKey("settings_haptics_enabled")
        val SHAKE = booleanPreferencesKey("settings_shake_enabled")
        val TILT = booleanPreferencesKey("settings_tilt_enabled")
    }
}
