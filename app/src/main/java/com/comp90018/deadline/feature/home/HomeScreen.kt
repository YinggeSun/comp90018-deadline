package com.comp90018.deadline.feature.home

import androidx.compose.runtime.Composable
import com.comp90018.deadline.core.ui.components.PlaceholderAction
import com.comp90018.deadline.core.ui.components.PlaceholderScreen

@Composable
fun HomeScreen(
    onPlay: () -> Unit,
    onLeaderboard: () -> Unit,
    onSettings: () -> Unit
) {
    PlaceholderScreen(
        title = "Home",
        actions = listOf(
            PlaceholderAction("Play", onPlay),
            PlaceholderAction("Leaderboard", onLeaderboard),
            PlaceholderAction("Settings", onSettings)
        )
    )
}
