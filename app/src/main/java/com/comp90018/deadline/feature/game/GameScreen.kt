package com.comp90018.deadline.feature.game

import androidx.compose.runtime.Composable
import com.comp90018.deadline.core.ui.components.PlaceholderAction
import com.comp90018.deadline.core.ui.components.PlaceholderScreen

@Composable
fun GameScreen(
    levelId: String,
    onGameFinished: () -> Unit,
    onBack: () -> Unit
) {
    PlaceholderScreen(
        title = "Game: $levelId",
        actions = listOf(
            PlaceholderAction("Finish game", onGameFinished),
            PlaceholderAction("Back", onBack)
        )
    )
}
