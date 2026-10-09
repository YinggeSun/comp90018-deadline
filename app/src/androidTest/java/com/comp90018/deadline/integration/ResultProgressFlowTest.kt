package com.comp90018.deadline.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.core.util.TimeFormatter
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.tileTestTag
import com.comp90018.deadline.navigation.AppNavHost
import com.comp90018.deadline.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real navigation, app container and DataStore: the Result screen shows the
 * saved Personal Best after a win and again after a replay, even though each
 * win is saved in the background after the Game screen closes. Assertions hold
 * whatever progress earlier runs left on the device.
 */
@RunWith(AndroidJUnit4::class)
class ResultProgressFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: TestNavHostController
    private val level = FixedLevels.LEVEL_1
    private val container get() = ApplicationProvider.getApplicationContext<DeadlineApp>().container

    @Before
    fun setUp() {
        composeRule.setContent {
            navController =
                TestNavHostController(LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            AppNavHost(navController = navController)
        }
    }

    private fun click(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun winLevel() {
        level.board.tiles.forEach { tile ->
            composeRule.onNodeWithTag(tileTestTag(tile.id)).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        assertEquals(Routes.RESULT, navController.currentBackStackEntry?.destination?.route)
    }

    private data class RunResult(val elapsedMillis: Long, val previousBestMillis: Long?)

    private fun currentRun(): RunResult {
        val arguments = navController.currentBackStackEntry?.arguments
        assertNotNull(arguments)
        assertTrue(arguments!!.getBoolean(Routes.ARG_WON))
        val previous = arguments.getLong(Routes.ARG_PREVIOUS_BEST_MILLIS)
        return RunResult(
            elapsedMillis = arguments.getLong(Routes.ARG_ELAPSED_MILLIS),
            previousBestMillis = previous.takeIf { it != Routes.NO_PREVIOUS_BEST },
        )
    }

    /** The Result screen shows [expectedBest] and announces a new best only when [newBest]. */
    private fun assertResultShows(
        expectedBest: Long,
        newBest: Boolean,
    ) {
        composeRule.onNodeWithText("Deadline met!").assertExists()
        composeRule.onNodeWithText("No best time yet").assertDoesNotExist()
        val bestText = TimeFormatter.formatSeconds(expectedBest / 1000)
        assertTrue(composeRule.onAllNodesWithText(bestText).fetchSemanticsNodes().isNotEmpty())
        if (newBest) {
            composeRule.onNodeWithText("New personal best!").assertExists()
        } else {
            composeRule.onNodeWithText("New personal best!").assertDoesNotExist()
        }
    }

    /** The background save of a win has landed once the stored best is at most that win's time. */
    private fun waitForSavedBestAtMost(timeMillis: Long) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val saved = container.currentProgress.value.bestFor(level.id)?.timeMillis
            saved != null && saved <= timeMillis.coerceAtLeast(1L)
        }
    }

    @Test
    fun resultShowsSavedPersonalBestAfterWinAndReplay() {
        click("Play")
        click(level.name)

        // First win: the best shown is the faster of the stored best (if any) and this run.
        winLevel()
        val first = currentRun()
        val firstNewBest = first.previousBestMillis == null || first.elapsedMillis < first.previousBestMillis
        val bestAfterFirst = if (firstNewBest) first.elapsedMillis else first.previousBestMillis!!
        assertResultShows(expectedBest = bestAfterFirst, newBest = firstNewBest)

        // The first win is saved in the background; replay only once it has landed.
        waitForSavedBestAtMost(first.elapsedMillis)
        val savedBest = container.currentProgress.value.bestFor(level.id)!!.timeMillis

        click("Retry")
        winLevel()
        val second = currentRun()

        // The replay compares against the saved best, not "no best yet".
        assertEquals(savedBest, second.previousBestMillis)
        val secondNewBest = second.elapsedMillis < savedBest
        assertResultShows(
            expectedBest = if (secondNewBest) second.elapsedMillis else savedBest,
            newBest = secondNewBest,
        )
    }
}
