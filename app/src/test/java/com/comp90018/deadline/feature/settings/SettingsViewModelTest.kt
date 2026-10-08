package com.comp90018.deadline.feature.settings

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.data.fake.FakeSettingsRepository
import com.comp90018.deadline.domain.settings.PlayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(initial: PlayerSettings = PlayerSettings()): Pair<SettingsViewModel, FakeSettingsRepository> {
        val repository = FakeSettingsRepository(initial)
        return SettingsViewModel(repository) to repository
    }

    @Test
    fun showsStoredSettings() {
        val (viewModel, _) =
            viewModel(PlayerSettings(nickname = "Hao", hapticsEnabled = false, tiltToPeekEnabled = false))
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hapticsEnabled)
        assertTrue(state.shakeToShuffleEnabled)
        assertFalse(state.tiltToPeekEnabled)
        assertEquals("Hao", state.savedNickname)
        assertEquals("Hao", state.nicknameDraft)
        assertTrue(state.isNicknameSaved)
    }

    @Test
    fun switchesSaveImmediately() {
        val (viewModel, repository) = viewModel()

        viewModel.setHapticsEnabled(false)
        viewModel.setShakeToShuffleEnabled(false)
        viewModel.setTiltToPeekEnabled(false)

        val stored = repository.settings.value
        assertFalse(stored.hapticsEnabled)
        assertFalse(stored.shakeToShuffleEnabled)
        assertFalse(stored.tiltToPeekEnabled)
        assertFalse(viewModel.uiState.value.hapticsEnabled)
    }

    @Test
    fun editingNicknameDoesNotSaveUntilAsked() {
        val (viewModel, repository) = viewModel()

        viewModel.onNicknameChange("Hao")

        assertNull(repository.settings.value.nickname)
        assertEquals("Hao", viewModel.uiState.value.nicknameDraft)
        assertTrue(viewModel.uiState.value.canSaveNickname)
    }

    @Test
    fun savingTrimsNickname() {
        val (viewModel, repository) = viewModel()

        viewModel.onNicknameChange("  Hao  ")
        viewModel.saveNickname()

        assertEquals("Hao", repository.settings.value.nickname)
        assertEquals("Hao", viewModel.uiState.value.nicknameDraft)
        assertTrue(viewModel.uiState.value.isNicknameSaved)
        assertFalse(viewModel.uiState.value.canSaveNickname)
    }

    @Test
    fun blankNicknameIsRejected() {
        val (viewModel, repository) = viewModel(PlayerSettings(nickname = "Hao"))

        viewModel.onNicknameChange("   ")
        viewModel.saveNickname()

        assertTrue(viewModel.uiState.value.isNicknameBlank)
        assertFalse(viewModel.uiState.value.canSaveNickname)
        assertEquals("Hao", repository.settings.value.nickname)
    }

    @Test
    fun nicknameIsLimitedToMaximumLength() {
        val (viewModel, _) = viewModel()

        viewModel.onNicknameChange("x".repeat(PlayerSettings.MAX_NICKNAME_LENGTH + 5))

        assertEquals(PlayerSettings.MAX_NICKNAME_LENGTH, viewModel.uiState.value.nicknameDraft.length)
        assertTrue(viewModel.uiState.value.canSaveNickname)
    }

    @Test
    fun unchangedNicknameCannotBeSavedAgain() {
        val (viewModel, _) = viewModel(PlayerSettings(nickname = "Hao"))

        assertFalse(viewModel.uiState.value.canSaveNickname)
        viewModel.onNicknameChange("Hao ")
        assertFalse(viewModel.uiState.value.canSaveNickname)
    }
}
