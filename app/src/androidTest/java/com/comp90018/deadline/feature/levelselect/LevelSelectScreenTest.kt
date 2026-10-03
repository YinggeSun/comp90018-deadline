package com.comp90018.deadline.feature.levelselect

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LevelSelectScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val unlocked = LevelItemUiModel("a", "Level A", 6, 1, isLocked = false, bestTimeSeconds = 83)
    private val locked = LevelItemUiModel("b", "Level B", 9, 2, isLocked = true, bestTimeSeconds = null)

    private fun setContent(onLevelSelected: (String) -> Unit = {}, onBack: () -> Unit = {}) {
        composeRule.setContent {
            DeadlineTheme {
                LevelSelectContent(
                    uiState = LevelSelectUiState(levels = listOf(unlocked, locked)),
                    onLevelSelected = onLevelSelected,
                    onBack = onBack
                )
            }
        }
    }

    @Test
    fun unlockedLevelSendsItsId() {
        val selected = mutableListOf<String>()
        setContent(onLevelSelected = { selected += it })

        composeRule.onNodeWithTag(levelCardTestTag("a")).performClick()

        assertEquals(listOf("a"), selected)
    }

    @Test
    fun lockedLevelIsDisabled() {
        val selected = mutableListOf<String>()
        setContent(onLevelSelected = { selected += it })

        composeRule.onNodeWithTag(levelCardTestTag("b"))
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
            .performClick()

        assertTrue(selected.isEmpty())
    }

    @Test
    fun showsDetailsAndPersonalBest() {
        setContent()

        composeRule.onNodeWithText("6 tiles · 1 layer", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("9 tiles · 2 layers", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Best 01:23", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("No best time yet", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun backButtonCallsBack() {
        var backs = 0
        setContent(onBack = { backs++ })

        composeRule.onNodeWithText("Back").performClick()

        assertEquals(1, backs)
    }
}
