package com.comp90018.deadline.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reads and changes the player's stored settings. Switches save immediately; the
 * nickname is edited as a draft and saved explicitly, since a half-typed name
 * should not reach the leaderboard.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    /** Null until the player edits the field; the stored nickname is shown until then. */
    private val nicknameDraft = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.settings, nicknameDraft) { settings, draft ->
            SettingsUiState(
                isLoading = false,
                hapticsEnabled = settings.hapticsEnabled,
                shakeToShuffleEnabled = settings.shakeToShuffleEnabled,
                tiltToPeekEnabled = settings.tiltToPeekEnabled,
                savedNickname = settings.nickname,
                nicknameDraft = draft ?: settings.nickname.orEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun setShakeToShuffleEnabled(enabled: Boolean) = update { it.copy(shakeToShuffleEnabled = enabled) }

    fun setTiltToPeekEnabled(enabled: Boolean) = update { it.copy(tiltToPeekEnabled = enabled) }

    /** Keeps at most [PlayerSettings.MAX_NICKNAME_LENGTH] characters in the field. */
    fun onNicknameChange(value: String) {
        nicknameDraft.value = value.take(PlayerSettings.MAX_NICKNAME_LENGTH)
    }

    fun saveNickname() {
        val state = uiState.value
        if (!state.canSaveNickname) return
        val nickname = state.trimmedNickname
        update { it.copy(nickname = nickname) }
        nicknameDraft.value = nickname
    }

    private fun update(transform: (PlayerSettings) -> PlayerSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    SettingsViewModel(container.settingsRepository)
                }
            }
    }
}
