package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A narrow phone with large fonts (320dp wide at 150%) keeps the game's labels whole:
 * the action buttons stay on one line and the level name is not cut short (#47).
 */
@RunWith(AndroidJUnit4::class)
class GameNarrowLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val level = FixedLevels.LEVEL_1

    private fun setNarrowContent() {
        val viewModel = GameViewModel(level.id)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                DeadlineTheme {
                    // requiredSize lays the screen out at this size on any test device.
                    Box(modifier = Modifier.requiredSize(width = 320.dp, height = 640.dp)) {
                        GameScreen(levelId = level.id, onGameFinished = {}, onBack = {}, viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    @Test
    fun actionLabelsStayOnOneLine() {
        setNarrowContent()

        listOf("Undo", "Restart").forEach { label ->
            assertEquals("$label wraps", 1, composeRule.onNodeWithText(label, useUnmergedTree = true).textLayout().lineCount)
        }
    }

    @Test
    fun levelNameIsShownInFullAndIsAHeading() {
        setNarrowContent()

        val title = composeRule.onNodeWithText(level.name)
        title.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertFalse("level name is cut short", title.textLayout().hasVisualOverflow)
    }
}
