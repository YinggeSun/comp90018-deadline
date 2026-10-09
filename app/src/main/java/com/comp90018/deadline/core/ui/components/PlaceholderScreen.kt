package com.comp90018.deadline.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.comp90018.deadline.core.theme.Spacing

/** A labelled button on a [PlaceholderScreen]. */
data class PlaceholderAction(val label: String, val onClick: () -> Unit)

/**
 * Temporary screen body used until each feature screen gets its real UI.
 * It only exists so every route can be reached and exercised.
 *
 * The first action is shown as the primary button and the rest as secondary.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    actions: List<PlaceholderAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        actions.forEachIndexed { index, action ->
            if (index == 0) {
                PrimaryButton(text = action.label, onClick = action.onClick)
            } else {
                SecondaryButton(text = action.label, onClick = action.onClick)
            }
        }
    }
}
