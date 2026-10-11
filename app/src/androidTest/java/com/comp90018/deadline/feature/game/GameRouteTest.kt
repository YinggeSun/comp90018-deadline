package com.comp90018.deadline.feature.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.ui.components.PHOTO_BACKGROUND_TAG
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.feature.game.components.tileTestTag
import com.comp90018.deadline.testing.TestLevels
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Game destination shows a loading state while a board is generated, and can retry. */
@RunWith(AndroidJUnit4::class)
class GameRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val levelId = "level_2"

    private fun setRoute(source: LevelSource) {
        composeRule.setContent {
            DeadlineTheme {
                GameRoute(levelId = levelId, levelSource = source, onGameFinished = {}, onBack = {})
            }
        }
    }

    @Test
    fun showsLoadingUntilTheBoardIsReady() {
        val ready = CompletableDeferred<Unit>()
        setRoute(
            LevelSource {
                ready.await()
                TestLevels.board(it)
            },
        )

        composeRule.onNodeWithText("Preparing your board…").assertIsDisplayed()
        composeRule.onNodeWithText("Level 2").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed()

        ready.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Preparing your board…").assertDoesNotExist()
        composeRule.onNodeWithTag(tileTestTag(TestLevels.board(levelId)!!.board.tiles.first().id)).assertIsDisplayed()
    }

    @Test
    fun failureOffersRetry() {
        var calls = 0
        setRoute(
            LevelSource {
                calls++
                if (calls == 1) null else TestLevels.board(it)
            },
        )

        composeRule.onNodeWithText("This level could not be loaded.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.waitForIdle()

        assertEquals(2, calls)
        composeRule.onNodeWithTag(tileTestTag(TestLevels.board(levelId)!!.board.tiles.first().id)).assertIsDisplayed()
    }

    @Test
    fun showsTheLevelsCampusPhoto() {
        setRoute(TestLevels.source)

        composeRule.onNodeWithTag(PHOTO_BACKGROUND_TAG).assertExists()
    }
}
