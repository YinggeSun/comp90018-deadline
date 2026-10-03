package com.comp90018.deadline.feature.home

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
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(state: HomeUiState) {
        composeRule.setContent {
            DeadlineTheme {
                HomeContent(
                    uiState = state,
                    onPlay = { calls += "play" },
                    onContinue = { calls += "continue:$it" },
                    onLeaderboard = { calls += "leaderboard" },
                    onSettings = { calls += "settings" }
                )
            }
        }
    }

    @Test
    fun showsTitleAndReachesEveryDestination() {
        setContent(HomeUiState())

        composeRule.onNodeWithText("Deadline!").assertIsDisplayed()
        composeRule.onNodeWithText("Play").performScrollTo().performClick()
        composeRule.onNodeWithText("Leaderboard").performScrollTo().performClick()
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()

        assertEquals(listOf("play", "leaderboard", "settings"), calls)
    }

    @Test
    fun continueIsHiddenWithoutProgress() {
        setContent(HomeUiState(continueLevelId = null))

        composeRule.onNodeWithText("Continue").assertDoesNotExist()
    }

    @Test
    fun continueOpensSavedLevel() {
        setContent(HomeUiState(continueLevelId = "level_2"))

        composeRule.onNodeWithText("Continue").performScrollTo().performClick()
        composeRule.onNodeWithText("Play").performScrollTo().assertIsDisplayed()

        assertEquals(listOf("continue:level_2"), calls)
    }
}
