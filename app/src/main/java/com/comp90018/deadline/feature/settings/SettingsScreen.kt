package com.comp90018.deadline.feature.settings

import androidx.compose.runtime.Composable
import com.comp90018.deadline.core.ui.components.PlaceholderAction
import com.comp90018.deadline.core.ui.components.PlaceholderScreen

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Settings",
        actions = listOf(PlaceholderAction("Back", onBack)),
    )
}
