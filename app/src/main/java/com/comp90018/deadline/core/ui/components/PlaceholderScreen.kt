package com.comp90018.deadline.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A labelled button on a [PlaceholderScreen]. */
data class PlaceholderAction(val label: String, val onClick: () -> Unit)

/**
 * Temporary screen body used until each feature screen gets its real UI.
 * It only exists so every route can be reached and exercised.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    actions: List<PlaceholderAction>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        actions.forEach { action ->
            Button(onClick = action.onClick) {
                Text(text = action.label)
            }
        }
    }
}
