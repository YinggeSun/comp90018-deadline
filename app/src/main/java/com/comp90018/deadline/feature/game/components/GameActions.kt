package com.comp90018.deadline.feature.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.SecondaryButton
import com.comp90018.deadline.core.ui.components.TertiaryButton

/**
 * Buttons below the Task Tray. Undo is enabled only when the game reports
 * something to undo.
 */
@Composable
fun GameActions(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(
            text = stringResource(R.string.game_action_undo),
            onClick = onUndo,
            enabled = canUndo,
        )
        TertiaryButton(
            text = stringResource(R.string.game_action_restart),
            onClick = onRestart,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameActionsPreview() {
    DeadlineTheme {
        Surface {
            GameActions(canUndo = true, onUndo = {}, onRestart = {}, modifier = Modifier.padding(Spacing.large))
        }
    }
}
