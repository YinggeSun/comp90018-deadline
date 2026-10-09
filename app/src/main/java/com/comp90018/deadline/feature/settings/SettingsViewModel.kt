package com.comp90018.deadline.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.core.util.takeWholeCharacters
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
 *
 * Saves run on [writeScope] (the app-wide scope in production), so a change the player
 * made is still written after they leave the screen. A failed save is reported through
 * [SettingsUiState.saveFailed] and can be retried; it never crashes the app.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val writeScope: CoroutineScope,
) : ViewModel() {
    /** Null until the player edits the field; the stored nickname is shown until then. */
    private val nicknameDraft = MutableStateFlow<String?>(null)

    /** The most recent change that failed to save, kept so it can be retried. */
    private val failedChange = MutableStateFlow<((PlayerSettings) -> PlayerSettings)?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.settings, nicknameDraft, failedChange) { settings, draft, failed ->
            SettingsUiState(
                isLoading = false,
                hapticsEnabled = settings.hapticsEnabled,
                shakeToShuffleEnabled = settings.shakeToShuffleEnabled,
                tiltToPeekEnabled = settings.tiltToPeekEnabled,
                savedNickname = settings.nickname,
                nicknameDraft = draft ?: settings.nickname.orEmpty(),
                saveFailed = failed != null,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun setShakeToShuffleEnabled(enabled: Boolean) = update { it.copy(shakeToShuffleEnabled = enabled) }

    fun setTiltToPeekEnabled(enabled: Boolean) = update { it.copy(tiltToPeekEnabled = enabled) }

    /**
     * Keeps the field within [PlayerSettings.MAX_NICKNAME_LENGTH], cutting only between whole
     * characters so an emoji is never split.
     */
    fun onNicknameChange(value: String) {
        nicknameDraft.value = value.takeWholeCharacters(PlayerSettings.MAX_NICKNAME_LENGTH)
    }

    fun saveNickname() {
        val state = uiState.value
        if (!state.canSaveNickname) return
        val nickname = state.trimmedNickname
        update { it.copy(nickname = nickname) }
        nicknameDraft.value = nickname
    }

    /** Saves the change that last failed again. */
    fun retrySave() {
        val change = failedChange.value ?: return
        update(change)
    }

    private fun update(transform: (PlayerSettings) -> PlayerSettings) {
        writeScope.launch {
            try {
                repository.update(transform)
                // A later successful save supersedes an older failure; the switches show the stored values.
                failedChange.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Reported on screen with a retry, rather than crashing the app.
                failedChange.value = transform
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    SettingsViewModel(container.settingsRepository, container.applicationScope)
                }
            }
    }
}
