package com.comp90018.deadline.feature.settings

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.SecondaryButton
import com.comp90018.deadline.core.ui.components.TertiaryButton

const val TRANSFER_CODE_TAG = "transfer_code"
const val TRANSFER_INPUT_TAG = "transfer_input"
const val TRANSFER_MESSAGE_TAG = "transfer_message"

/** The "Move to another phone" section of Settings (#39). */
@Composable
fun TransferProgressSection(viewModel: TransferProgressViewModel = viewModel(factory = TransferProgressViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsState()
    TransferProgressContent(
        uiState = uiState,
        onGetCode = viewModel::createCode,
        onCodeInputChange = viewModel::onCodeInputChange,
        onRedeem = viewModel::redeem,
    )
}

/** Stateless body of the transfer section, driven only by [uiState]. */
@Composable
fun TransferProgressContent(
    uiState: TransferProgressUiState,
    onGetCode: () -> Unit,
    onCodeInputChange: (String) -> Unit,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(uiState.code) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Text(
            text = stringResource(R.string.transfer_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // This phone's code.
        if (uiState.code == null) {
            SecondaryButton(
                text = stringResource(if (uiState.isCreating) R.string.transfer_creating else R.string.transfer_get_code),
                onClick = onGetCode,
                enabled = !uiState.isCreating,
            )
        } else {
            Text(text = stringResource(R.string.transfer_code_label), style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uiState.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).testTag(TRANSFER_CODE_TAG),
                )
                TertiaryButton(
                    text = stringResource(if (copied) R.string.transfer_copied else R.string.transfer_copy),
                    onClick = {
                        clipboard.setText(AnnotatedString(uiState.code))
                        copied = true
                    },
                )
            }
            uiState.expiresAtMillis?.let { expiresAt ->
                Text(
                    text = stringResource(R.string.transfer_code_expires, formatExpiry(expiresAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Another phone's code.
        OutlinedTextField(
            value = uiState.codeInput,
            onValueChange = onCodeInputChange,
            modifier = Modifier.fillMaxWidth().testTag(TRANSFER_INPUT_TAG),
            label = { Text(stringResource(R.string.transfer_enter_label)) },
            singleLine = true,
            enabled = !uiState.isRedeeming,
            isError = uiState.message == TransferMessage.INVALID_CODE || uiState.message == TransferMessage.NOT_FOUND,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions = KeyboardActions(onDone = { if (uiState.canRedeem) onRedeem() }),
        )
        SecondaryButton(
            text = stringResource(if (uiState.isRedeeming) R.string.transfer_redeeming else R.string.transfer_redeem),
            onClick = onRedeem,
            enabled = uiState.canRedeem,
            modifier = Modifier.align(Alignment.End),
        )

        uiState.message?.let { message ->
            Text(
                text = messageText(message, uiState),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (message == TransferMessage.REDEEMED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                modifier =
                    Modifier
                        .testTag(TRANSFER_MESSAGE_TAG)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun messageText(
    message: TransferMessage,
    uiState: TransferProgressUiState,
): String =
    when (message) {
        TransferMessage.REDEEMED -> stringResource(R.string.transfer_redeemed, uiState.clearedLevels, uiState.unlockedWeek)
        TransferMessage.INVALID_CODE -> stringResource(R.string.transfer_invalid_code)
        TransferMessage.NOT_FOUND -> stringResource(R.string.transfer_not_found)
        TransferMessage.CODE_OFFLINE, TransferMessage.REDEEM_OFFLINE -> stringResource(R.string.transfer_offline)
        TransferMessage.CODE_FAILED, TransferMessage.REDEEM_FAILED -> stringResource(R.string.transfer_failed)
    }

/** For example "Sat 9:30 pm", in the device's own time format. */
@Composable
private fun formatExpiry(millis: Long): String =
    DateUtils.formatDateTime(
        LocalContext.current,
        millis,
        DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY,
    )

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransferProgressPreview() {
    DeadlineTheme {
        Surface {
            TransferProgressContent(
                uiState =
                    TransferProgressUiState(
                        code = "K7QM-3XPD",
                        expiresAtMillis = 1_791_715_200_000,
                        codeInput = "AB",
                    ),
                onGetCode = {},
                onCodeInputChange = {},
                onRedeem = {},
            )
        }
    }
}
