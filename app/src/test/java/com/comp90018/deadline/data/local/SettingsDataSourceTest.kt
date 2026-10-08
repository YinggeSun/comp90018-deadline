package com.comp90018.deadline.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsDataSourceTest {
    @get:Rule
    val store = DataStoreTestRule()

    @Test
    fun emptyStoreReadsAsDefaults() =
        runBlocking {
            assertEquals(PlayerSettings(), SettingsDataSource(store.dataStore).settings.first())
        }

    @Test
    fun settingsAndNicknameSurviveRestart() =
        runBlocking {
            SettingsDataSource(store.dataStore).update {
                it.copy(nickname = "Lavanya", hapticsEnabled = false, shakeToShuffleEnabled = false)
            }

            val restored = SettingsDataSource(store.reopen()).settings.first()

            assertEquals("Lavanya", restored.nickname)
            assertFalse(restored.hapticsEnabled)
            assertFalse(restored.shakeToShuffleEnabled)
            assertEquals(true, restored.tiltToPeekEnabled)
        }

    @Test
    fun clearingNicknameRemovesIt() =
        runBlocking {
            val source = SettingsDataSource(store.dataStore)
            source.update { it.copy(nickname = "Lavanya") }

            source.update { it.copy(nickname = null) }

            assertNull(source.settings.first().nickname)
        }

    @Test
    fun invalidStoredNicknameReadsAsUnset() =
        runBlocking {
            store.dataStore.edit { it[stringPreferencesKey("settings_nickname")] = "x".repeat(50) }

            assertNull(SettingsDataSource(store.dataStore).settings.first().nickname)
        }
}
