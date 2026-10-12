package com.comp90018.deadline.feature.leaderboard

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeaderboardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val levels = (1..6).map { LeaderboardLevelTab("level_$it", "Level $it") }

    private val rows =
        listOf(
            LeaderboardRowUiModel(1, "Alex", 41_230, isCurrentPlayer = false),
            LeaderboardRowUiModel(2, "Hao", 52_070, isCurrentPlayer = true),
        )

    private fun setContent(
        content: LeaderboardContent,
        selectedLevelId: String = "level_1",
        onLevelSelected: (String) -> Unit = {},
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            DeadlineTheme {
                LeaderboardContent(
                    uiState = LeaderboardUiState(levels, selectedLevelId, content),
                    onLevelSelected = onLevelSelected,
                    onRetry = onRetry,
                    onBack = onBack,
                )
            }
        }
    }

    private fun description(index: Int) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(rowText(index)))

    private fun rowText(index: Int) =
        when (index) {
            0 -> "Rank 1, Alex, 00:41.23"
            else -> "Rank 2, Hao, 00:52.07, you"
        }

    @Test
    fun showsRankNameAndTimeAndMarksThePlayer() {
        setContent(LeaderboardContent.Ranked(rows))

        composeRule.onNodeWithTag(leaderboardRowTestTag(0)).assertIsDisplayed().assert(description(0))
        composeRule.onNodeWithTag(leaderboardRowTestTag(1)).assertIsDisplayed().assert(description(1))
    }

    @Test
    fun selectedLevelTabIsSelectedAndOtherTabsSendTheirId() {
        val selected = mutableListOf<String>()
        setContent(LeaderboardContent.Ranked(rows), selectedLevelId = "level_2", onLevelSelected = { selected += it })

        composeRule.onNodeWithText("Level 2").assertIsSelected()
        composeRule.onNodeWithText("Level 3").performClick()

        assertEquals(listOf("level_3"), selected)
    }

    @Test
    fun emptyRankingInvitesThePlayerToSetTheFirstTime() {
        setContent(LeaderboardContent.Ranked(emptyList()), selectedLevelId = "level_4")

        composeRule.onNodeWithText("No times yet").assertIsDisplayed()
        composeRule.onNodeWithText("Clear Level 4 to set the first time on this leaderboard.").assertIsDisplayed()
    }

    @Test
    fun offlineWithCachedRankingShowsBannerAndRows() {
        var retries = 0
        setContent(LeaderboardContent.Offline(rows), onRetry = { retries++ })

        composeRule.onNodeWithTag(LEADERBOARD_OFFLINE_BANNER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(leaderboardRowTestTag(0)).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun offlineWithoutRankingExplainsAndOffersRetry() {
        var retries = 0
        setContent(LeaderboardContent.Offline(emptyList()), onRetry = { retries++ })

        composeRule.onNodeWithText("You are offline").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun errorShowsItsReasonAndRetry() {
        var retries = 0
        setContent(LeaderboardContent.Error(LeaderboardFailure.NOT_SIGNED_IN), onRetry = { retries++ })

        composeRule.onNodeWithText("Could not connect your player profile to the leaderboard.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun loadingShowsProgressMessage() {
        setContent(LeaderboardContent.Loading)

        composeRule.onNodeWithText("Loading the leaderboard…").assertIsDisplayed()
    }

    @Test
    fun backIsSent() {
        var backs = 0
        setContent(LeaderboardContent.Ranked(rows), onBack = { backs++ })

        composeRule.onNodeWithText("Back").performClick()

        assertEquals(1, backs)
    }
}
