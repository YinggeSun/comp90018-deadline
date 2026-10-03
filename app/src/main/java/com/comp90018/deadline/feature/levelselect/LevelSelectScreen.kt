package com.comp90018.deadline.feature.levelselect

import androidx.compose.runtime.Composable
import com.comp90018.deadline.core.ui.components.PlaceholderAction
import com.comp90018.deadline.core.ui.components.PlaceholderScreen
import com.comp90018.deadline.domain.level.model.FixedLevels

@Composable
fun LevelSelectScreen(
    onLevelSelected: (levelId: String) -> Unit,
    onBack: () -> Unit
) {
    PlaceholderScreen(
        title = "Level Select",
        actions = listOf(
            PlaceholderAction("Start sample level") { onLevelSelected(FixedLevels.SAMPLE_LEVEL.id) },
            PlaceholderAction("Back", onBack)
        )
    )
}
