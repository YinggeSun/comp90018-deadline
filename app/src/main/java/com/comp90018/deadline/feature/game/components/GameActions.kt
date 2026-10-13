package com.comp90018.deadline.feature.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.SecondaryButton
import com.comp90018.deadline.core.ui.components.TertiaryButton

/** Keeps the two buttons a comfortable size on tablets and in landscape. */
private val MaxActionsWidth = 420.dp

/**
 * Buttons below the Task Tray. Undo is enabled only when the game reports
 * something to undo. The two buttons share the row equally, so neither label
 * is squeezed onto two lines on a narrow screen or with large fonts.
 */
@Composable
fun GameActions(
    canUndo: Boolean,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.widthIn(max = MaxActionsWidth).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryButton(
            text = stringResource(R.string.game_action_undo),
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.weight(1f),
        )
        TertiaryButton(
            text = stringResource(R.string.game_action_restart),
            onClick = onRestart,
            modifier = Modifier.weight(1f),
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
