package com.comp90018.deadline.feature.result

import androidx.compose.runtime.Composable
import com.comp90018.deadline.core.ui.components.PlaceholderAction
import com.comp90018.deadline.core.ui.components.PlaceholderScreen

@Composable
fun ResultScreen(
    levelId: String,
    onReplay: () -> Unit,
    onLevelSelect: () -> Unit,
    onHome: () -> Unit
) {
    PlaceholderScreen(
        title = "Result: $levelId",
        actions = listOf(
            PlaceholderAction("Replay", onReplay),
            PlaceholderAction("Level Select", onLevelSelect),
            PlaceholderAction("Home", onHome)
        )
    )
}
