package com.comp90018.deadline.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Regression tests for saving settings when storage fails, is slow, or the input has emoji. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSaveReliabilityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** In-memory settings whose writes can fail or take time, like a real disk. */
    private class UnreliableSettingsRepository(
        initial: PlayerSettings = PlayerSettings(),
    ) : SettingsRepository {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<PlayerSettings> = state
        var failWrites = false
        var writeDelayMillis = 0L

        override suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
            if (writeDelayMillis > 0) delay(writeDelayMillis)
            if (failWrites) throw IOException("disk unavailable")
            state.update(transform)
        }
    }

    private fun immediateViewModel(repository: SettingsRepository) =
        SettingsViewModel(repository, CoroutineScope(mainDispatcherRule.dispatcher))

    @Test
    fun failedSaveIsReportedInsteadOfCrashing() {
        val repository = UnreliableSettingsRepository().apply { failWrites = true }
        val viewModel = immediateViewModel(repository)

        viewModel.setHapticsEnabled(false)

        val state = viewModel.uiState.value
        assertTrue(state.saveFailed)
        // The switch shows what is actually stored.
        assertTrue(state.hapticsEnabled)
        assertTrue(repository.settings.value.hapticsEnabled)
    }

    @Test
    fun retrySavesTheFailedChange() {
        val repository = UnreliableSettingsRepository().apply { failWrites = true }
        val viewModel = immediateViewModel(repository)
        viewModel.setHapticsEnabled(false)

        repository.failWrites = false
        viewModel.retrySave()

        assertFalse(repository.settings.value.hapticsEnabled)
        assertFalse(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.hapticsEnabled)
    }

    @Test
    fun laterSuccessfulSaveClearsTheFailure() {
        val repository = UnreliableSettingsRepository().apply { failWrites = true }
        val viewModel = immediateViewModel(repository)
        viewModel.setHapticsEnabled(false)

        repository.failWrites = false
        viewModel.setTiltToPeekEnabled(false)

        assertFalse(viewModel.uiState.value.saveFailed)
        assertFalse(repository.settings.value.tiltToPeekEnabled)
    }

    @Test
    fun changeIsSavedAfterLeavingSettings() =
        runTest {
            val repository = UnreliableSettingsRepository().apply { writeDelayMillis = 500 }
            val writeScope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            val viewModel =
                ViewModelProvider(
                    store,
                    viewModelFactory { initializer { SettingsViewModel(repository, writeScope) } },
                )[SettingsViewModel::class.java]

            viewModel.setHapticsEnabled(false)
            // Leaving Settings clears its ViewModel while the write is still in progress.
            store.clear()
            advanceUntilIdle()

            assertFalse(repository.settings.value.hapticsEnabled)
        }

    @Test
    fun nicknameIsNeverCutInsideAnEmoji() {
        val repository = UnreliableSettingsRepository()
        val viewModel = immediateViewModel(repository)
        val letters = "a".repeat(PlayerSettings.MAX_NICKNAME_LENGTH - 1)

        // The emoji takes two UTF-16 units, so keeping half of it would fit but corrupt it.
        viewModel.onNicknameChange(letters + "😀")
        assertEquals(letters, viewModel.uiState.value.nicknameDraft)

        viewModel.saveNickname()
        assertEquals(letters, repository.settings.value.nickname)
        assertEquals(letters, viewModel.uiState.value.savedNickname)
    }

    @Test
    fun emojiThatFitsIsKeptWhole() {
        val viewModel = immediateViewModel(UnreliableSettingsRepository())
        val name = "a".repeat(PlayerSettings.MAX_NICKNAME_LENGTH - 2) + "😀"

        viewModel.onNicknameChange(name)

        assertEquals(name, viewModel.uiState.value.nicknameDraft)
    }
}
