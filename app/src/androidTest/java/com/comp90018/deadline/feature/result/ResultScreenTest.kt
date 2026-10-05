package com.comp90018.deadline.feature.result

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(state: ResultUiState) {
        composeRule.setContent {
            DeadlineTheme {
                ResultContent(
                    uiState = state,
                    onRetry = { calls += "retry" },
                    onNextLevel = { calls += "next:$it" },
                    onLevelSelect = { calls += "levelSelect" },
                    onHome = { calls += "home" }
                )
            }
        }
    }

    @Test
    fun winShowsTimeAndNextLevel() {
        setContent(ResultUiState(levelName = "Level 1", won = true, elapsedMillis = 83_000, nextLevelId = "level_2"))

        composeRule.onNodeWithText("Deadline met!").assertIsDisplayed()
        composeRule.onNodeWithText("01:23").assertIsDisplayed()
        composeRule.onNodeWithText("No best time yet").assertIsDisplayed()
        composeRule.onNodeWithText("Next Level").performScrollTo().performClick()

        assertEquals(listOf("next:level_2"), calls)
    }

    @Test
    fun lossShowsRetryWithoutNextLevel() {
        setContent(ResultUiState(levelName = "Level 3", won = false, elapsedMillis = 41_000))

        composeRule.onNodeWithText("Deadline missed").assertIsDisplayed()
        composeRule.onNodeWithText("Next Level").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun newBestIsAnnounced() {
        setContent(ResultUiState(won = true, elapsedMillis = 50_000, bestTimeMillis = 60_000, isNewBest = true))

        composeRule.onNodeWithText("01:00").assertIsDisplayed()
        composeRule.onNodeWithText("New personal best!").assertIsDisplayed()
    }

    @Test
    fun everyActionIsReachable() {
        setContent(ResultUiState(won = true, nextLevelId = "level_2"))

        listOf("Retry", "Level Select", "Home").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().performClick()
        }

        assertEquals(listOf("retry", "levelSelect", "home"), calls)
    }
}
