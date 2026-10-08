package com.comp90018.deadline.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * A readable Preferences file can still hold a value of the wrong type under one of our keys,
 * for example after a schema change. Such values must read as defaults, and later writes
 * must replace them, instead of throwing ClassCastException.
 */
class WrongTypeRecoveryTest {
    @get:Rule
    val store = DataStoreTestRule()

    @Test
    fun progressWithWrongTypesReadsAsDefaults() =
        runBlocking {
            store.dataStore.edit {
                it[stringPreferencesKey("progress_highest_unlocked_week")] = "three"
                it[intPreferencesKey("progress_completed_level_ids")] = 7
                it[longPreferencesKey("progress_personal_bests")] = 42L
                it[stringPreferencesKey("progress_last_modified_millis")] = "yesterday"
            }

            assertEquals(PlayerProgress(), ProgressDataSource(store.dataStore).progress.first())
        }

    @Test
    fun progressWithWrongTypesCanBeSavedOverAndSurvivesRestart() =
        runBlocking {
            store.dataStore.edit {
                it[stringPreferencesKey("progress_highest_unlocked_week")] = "three"
                it[intPreferencesKey("progress_personal_bests")] = 1
            }

            ProgressDataSource(store.dataStore).recordCompletion(CompletionResult("level_1", 1, 42_000, 5))
            val restored = ProgressDataSource(store.reopen()).progress.first()

            assertEquals(2, restored.highestUnlockedWeek)
            assertEquals(42_000L, restored.bestFor("level_1")?.timeMillis)
        }

    @Test
    fun settingsWithWrongTypesReadAsDefaults() =
        runBlocking {
            store.dataStore.edit {
                it[stringPreferencesKey("settings_haptics_enabled")] = "yes"
                it[intPreferencesKey("settings_nickname")] = 5
            }

            assertEquals(PlayerSettings(), SettingsDataSource(store.dataStore).settings.first())
        }

    @Test
    fun settingsWithWrongTypesCanBeSavedOverAndSurviveRestart() =
        runBlocking {
            store.dataStore.edit { it[stringPreferencesKey("settings_haptics_enabled")] = "yes" }

            SettingsDataSource(store.dataStore).update { it.copy(hapticsEnabled = false, nickname = "Lav") }
            val restored = SettingsDataSource(store.reopen()).settings.first()

            assertFalse(restored.hapticsEnabled)
            assertEquals("Lav", restored.nickname)
        }
}
