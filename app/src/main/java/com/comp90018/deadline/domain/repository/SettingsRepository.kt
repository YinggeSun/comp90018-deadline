package com.comp90018.deadline.domain.repository

import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.Flow

/** Player preferences and nickname, stored on this device. */
interface SettingsRepository {
    /** Current settings, re-emitted on every change; defaults when nothing is stored. */
    val settings: Flow<PlayerSettings>

    /** Atomically replaces the settings with [transform] of the current value. */
    suspend fun update(transform: (PlayerSettings) -> PlayerSettings)
}
