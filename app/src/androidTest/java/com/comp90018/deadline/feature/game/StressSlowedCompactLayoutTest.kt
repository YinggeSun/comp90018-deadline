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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.feature.game.components.STRESS_SLOWED_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Music "Slowed" label must stay visible on a narrow screen with large fonts, even when a
 * stress warning shares the HUD (regression for review of PR #107).
 */
@RunWith(AndroidJUnit4::class)
class StressSlowedCompactLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val baseState =
        GameUiState(
            levelName = "Test Level",
            boardTiles = listOf(TileUiModel("b1", TileType.BOOK, 0, 0, 0, isSelectable = true)),
            boardRows = 2,
            boardColumns = 2,
            trayCapacity = 7,
            stress = 90,
            isStressSlowed = true,
        )

    private fun setCompactContent(state: GameUiState) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DeadlineTheme {
                    Box(modifier = Modifier.size(width = 320.dp, height = 640.dp)) {
                        GameContent(uiState = state, elapsedSeconds = 0, onEvent = {}, onBack = {})
                    }
                }
            }
        }
    }

    private fun assertSlowedLabelVisible() {
        composeRule
            .onNodeWithTag(STRESS_SLOWED_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(1.dp)
            .assertHeightIsAtLeast(1.dp)
    }

    @Test
    fun slowedLabelStaysVisibleBesideHighStressWarning() {
        setCompactContent(baseState.copy(isHighStress = true))

        assertSlowedLabelVisible()
    }

    @Test
    fun slowedLabelStaysVisibleBesideMaximumStressWarning() {
        setCompactContent(baseState.copy(stress = 100, isHighStress = true, isMaxStress = true))

        assertSlowedLabelVisible()
    }
}
