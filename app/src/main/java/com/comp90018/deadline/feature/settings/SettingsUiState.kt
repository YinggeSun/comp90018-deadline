package com.comp90018.deadline.feature.settings

import com.comp90018.deadline.domain.settings.PlayerSettings

/**
 * Settings screen state. [isLoading] is true until stored settings are read, so the
 * switches never flash their defaults. [nicknameDraft] is what the field shows;
 * [savedNickname] is what is stored. Switches always show the stored value, so after a
 * failed save they show what is actually kept.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val shakeToShuffleEnabled: Boolean = true,
    val tiltToPeekEnabled: Boolean = true,
    val savedNickname: String? = null,
    val nicknameDraft: String = "",
    /** The last change could not be saved; the screen offers to retry it. */
    val saveFailed: Boolean = false,
) {
    /** The draft as it would be stored: surrounding spaces removed. */
    val trimmedNickname: String get() = nicknameDraft.trim()

    val isNicknameBlank: Boolean get() = nicknameDraft.isNotEmpty() && trimmedNickname.isEmpty()

    val canSaveNickname: Boolean
        get() =
            !isLoading &&
                trimmedNickname.isNotEmpty() &&
                trimmedNickname.length <= PlayerSettings.MAX_NICKNAME_LENGTH &&
                trimmedNickname != savedNickname

    val isNicknameSaved: Boolean get() = savedNickname != null && trimmedNickname == savedNickname
}
