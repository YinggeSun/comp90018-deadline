package com.comp90018.deadline.feature.leaderboard

import androidx.compose.runtime.Composable
import com.comp90018.deadline.core.ui.components.PlaceholderAction
import com.comp90018.deadline.core.ui.components.PlaceholderScreen

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    PlaceholderScreen(
        title = "Leaderboard",
        actions = listOf(PlaceholderAction("Back", onBack))
    )
}
