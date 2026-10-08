package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Dimens
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.MinTileSize
import com.comp90018.deadline.feature.game.components.tileTestTag
import com.comp90018.deadline.feature.game.components.trayTileTestTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A short window with large fonts (e.g. landscape at 200%) must not shrink
 * the board below a usable size; the screen scrolls instead.
 */
@RunWith(AndroidJUnit4::class)
class GameCompactLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val level = FixedLevels.LEVEL_3

    private fun setCompactContent() {
        val viewModel = GameViewModel(level.id)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DeadlineTheme {
                    Box(modifier = Modifier.size(width = 400.dp, height = 320.dp)) {
                        GameScreen(levelId = level.id, onGameFinished = {}, onBack = {}, viewModel = viewModel)
                    }
                }
            }
        }
    }

    @Test
    fun boardTilesKeepMinimumSize() {
        setCompactContent()

        level.board.tiles.forEach { tile ->
            composeRule.onNodeWithTag(tileTestTag(tile.id))
                .assertWidthIsAtLeast(MinTileSize)
                .assertHeightIsAtLeast(MinTileSize)
        }
    }

    @Test
    fun boardTileCanBeScrolledToAndTapped() {
        setCompactContent()

        composeRule.onNodeWithTag(tileTestTag("level_3_laptop_3"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithTag(trayTileTestTag("level_3_laptop_3")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun actionsStayReachable() {
        setCompactContent()

        listOf("Undo", "Restart").forEach { label ->
            composeRule.onNodeWithText(label)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(Dimens.buttonMinHeight)
        }
    }
}
