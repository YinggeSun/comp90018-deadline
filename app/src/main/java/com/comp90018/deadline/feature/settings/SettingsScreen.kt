package com.comp90018.deadline.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.LoadingContent
import com.comp90018.deadline.core.ui.components.SecondaryButton
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.domain.settings.PlayerSettings

const val NICKNAME_FIELD_TAG = "settings_nickname"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onBackgroundMusicChange = viewModel::setBackgroundMusicEnabled,
        onSoundEffectsChange = viewModel::setSoundEffectsEnabled,
        onHapticsChange = viewModel::setHapticsEnabled,
        onShakeChange = viewModel::setShakeToShuffleEnabled,
        onTiltChange = viewModel::setTiltToPeekEnabled,
        onNicknameChange = viewModel::onNicknameChange,
        onSaveNickname = viewModel::saveNickname,
        onRetrySave = viewModel::retrySave,
    )
}

/** Stateless body of the Settings screen, driven only by [uiState]. */
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onShakeChange: (Boolean) -> Unit,
    onTiltChange: (Boolean) -> Unit,
    onNicknameChange: (String) -> Unit,
    onSaveNickname: () -> Unit,
    onRetrySave: () -> Unit,
    modifier: Modifier = Modifier,
    onBackgroundMusicChange: (Boolean) -> Unit = {},
    onSoundEffectsChange: (Boolean) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TertiaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (uiState.isLoading) {
            LoadingContent()
            return
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.large, vertical = Spacing.small),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            if (uiState.saveFailed) SaveFailedBanner(onRetry = onRetrySave)
            SectionHeader(stringResource(R.string.settings_section_feedback))
            SettingSwitch(
                title = stringResource(R.string.settings_background_music),
                summary = stringResource(R.string.settings_background_music_summary),
                checked = uiState.backgroundMusicEnabled,
                onCheckedChange = onBackgroundMusicChange,
            )
            SettingSwitch(
                title = stringResource(R.string.settings_sound_effects),
                summary = stringResource(R.string.settings_sound_effects_summary),
                checked = uiState.soundEffectsEnabled,
                onCheckedChange = onSoundEffectsChange,
            )
            SettingSwitch(
                title = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_summary),
                checked = uiState.hapticsEnabled,
                onCheckedChange = onHapticsChange,
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_motion))
            SettingSwitch(
                title = stringResource(R.string.settings_shake),
                summary = stringResource(R.string.settings_shake_summary),
                checked = uiState.shakeToShuffleEnabled,
                onCheckedChange = onShakeChange,
            )
            SettingSwitch(
                title = stringResource(R.string.settings_tilt),
                summary = stringResource(R.string.settings_tilt_summary),
                checked = uiState.tiltToPeekEnabled,
                onCheckedChange = onTiltChange,
            )

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_leaderboard))
            NicknameField(
                uiState = uiState,
                onNicknameChange = onNicknameChange,
                onSaveNickname = onSaveNickname,
            )
        }
    }
}

/** Shown after a failed save; announced by screen readers when it appears. */
@Composable
private fun SaveFailedBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.large, end = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TertiaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

/** A whole-row switch: tapping the text toggles it too, and it reads as one control. */
@Composable
private fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(Spacing.large))
        // The row handles the toggle, so the switch itself is display-only.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NicknameField(
    uiState: SettingsUiState,
    onNicknameChange: (String) -> Unit,
    onSaveNickname: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
            text = stringResource(R.string.settings_nickname_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.nicknameDraft,
            onValueChange = onNicknameChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(NICKNAME_FIELD_TAG),
            label = { Text(stringResource(R.string.settings_nickname)) },
            singleLine = true,
            isError = uiState.isNicknameBlank,
            supportingText = {
                Text(
                    text =
                        when {
                            uiState.isNicknameBlank -> stringResource(R.string.settings_nickname_blank)
                            uiState.isNicknameSaved -> stringResource(R.string.settings_nickname_saved)
                            else ->
                                stringResource(
                                    R.string.settings_nickname_count,
                                    uiState.nicknameDraft.length,
                                    PlayerSettings.MAX_NICKNAME_LENGTH,
                                )
                        },
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSaveNickname() }),
        )
        SecondaryButton(
            text = stringResource(R.string.settings_nickname_save),
            onClick = onSaveNickname,
            enabled = uiState.canSaveNickname,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreview() {
    DeadlineTheme {
        Surface {
            SettingsContent(
                uiState =
                    SettingsUiState(
                        isLoading = false,
                        shakeToShuffleEnabled = false,
                        savedNickname = "Hao",
                        nicknameDraft = "Hao",
                    ),
                onBack = {},
                onHapticsChange = {},
                onShakeChange = {},
                onTiltChange = {},
                onNicknameChange = {},
                onSaveNickname = {},
                onRetrySave = {},
            )
        }
    }
}
