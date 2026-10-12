package com.comp90018.deadline.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.GameOutcome
import com.comp90018.deadline.feature.game.components.tileTestTag
import com.comp90018.deadline.testing.TestLevels
import com.comp90018.deadline.testing.UiScreenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: TestNavHostController

    private val level = FixedLevels.LEVEL_1
    private val levelId = level.id

    @Before
    fun setUp() {
        composeRule.setContent {
            navController =
                TestNavHostController(LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            DeadlineTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    AppNavHost(modifier = Modifier.padding(padding), navController = navController, levelSource = TestLevels.source)
                }
            }
        }
    }

    private fun currentRoute() = navController.currentBackStackEntry?.destination?.route

    private fun currentArguments() = navController.currentBackStackEntry?.arguments

    private fun click(label: String) {
        val node = composeRule.onNodeWithText(label)
        if (label != "Back") node.performScrollTo()
        node.performClick()
        composeRule.waitForIdle()
    }

    /** Wins Level 1 by selecting every tile in board order; all of them start uncovered. */
    private fun finishGame() {
        level.board.tiles.forEach { tile ->
            composeRule.onNodeWithTag(tileTestTag(tile.id))
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun startsAtHome() {
        assertEquals(Routes.HOME, currentRoute())
    }

    @Test
    fun homeReachesLevelSelectLeaderboardAndSettings() {
        click("Play")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())
        click("Back")

        click("Leaderboard")
        assertEquals(Routes.LEADERBOARD, currentRoute())
        click("Back")

        click("Settings")
        assertEquals(Routes.SETTINGS, currentRoute())
        click("Back")

        assertEquals(Routes.HOME, currentRoute())
    }

    @Test
    fun gameRouteReceivesLevelId() {
        click("Play")
        click(level.name)

        assertEquals(Routes.GAME, currentRoute())
        assertEquals(
            levelId,
            navController.currentBackStackEntry?.arguments?.getString(Routes.ARG_LEVEL_ID),
        )
        composeRule.onNodeWithText(level.name).assertExists()
        composeRule.onNodeWithTag(tileTestTag(level.board.tiles.first().id)).assertExists()
    }

    @Test
    fun backFromResultSkipsFinishedGame() {
        click("Play")
        click(level.name)
        finishGame()
        assertEquals(Routes.RESULT, currentRoute())

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()

        assertEquals(Routes.LEVEL_SELECT, currentRoute())
    }

    @Test
    fun resultActionsNavigateToExpectedScreens() {
        click("Play")
        click(level.name)
        finishGame()

        click("Retry")
        assertEquals(Routes.GAME, currentRoute())
        assertEquals(levelId, currentArguments()?.getString(Routes.ARG_LEVEL_ID))

        finishGame()
        click("Level Select")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())

        click(level.name)
        finishGame()
        click("Home")
        assertEquals(Routes.HOME, currentRoute())
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun resultReceivesOutcomeOfFinishedGame() {
        click("Play")
        click(level.name)
        finishGame()

        assertEquals(Routes.RESULT, currentRoute())
        assertEquals(levelId, currentArguments()?.getString(Routes.ARG_LEVEL_ID))
        assertTrue(currentArguments()?.getBoolean(Routes.ARG_WON) == true)
        assertTrue((currentArguments()?.getLong(Routes.ARG_ELAPSED_MILLIS) ?: -1L) >= 0L)
        composeRule.onNodeWithText("Deadline met!").assertExists()
        UiScreenshot.capture(composeRule, "result-win")
    }

    @Test
    fun nextLevelOpensFollowingLevel() {
        click("Play")
        click(level.name)
        finishGame()

        click("Next Level")

        assertEquals(Routes.GAME, currentRoute())
        assertEquals(FixedLevels.LEVEL_2.id, currentArguments()?.getString(Routes.ARG_LEVEL_ID))
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()
        assertEquals(Routes.LEVEL_SELECT, currentRoute())
    }

    @Test
    fun backFromGameReturnsToLevelSelectWithoutShowingResult() {
        click("Play")
        click(level.name)
        click("Back")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())
        composeRule.onNodeWithText("Deadline met!").assertDoesNotExist()
        click("Back")
        assertEquals(Routes.HOME, currentRoute())
    }

    @Test
    fun backAfterRetrySkipsPreviousResult() {
        click("Play")
        click(level.name)
        finishGame()
        click("Retry")
        click("Back")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())
        click("Back")
        assertEquals(Routes.HOME, currentRoute())
    }

    @Test
    fun losingOutcomePreservesArgumentsAndRetrySkipsResult() {
        click("Play")
        click(level.name)
        // Inject an outcome to test navigation independently of the board's loss rules.
        composeRule.runOnUiThread {
            NavigationActions(navController).navigateToResult(levelId, GameOutcome(false, 41_000, 30_000))
        }
        composeRule.waitForIdle()
        assertEquals(Routes.RESULT, currentRoute())
        assertEquals(false, currentArguments()?.getBoolean(Routes.ARG_WON))
        assertEquals(41_000L, currentArguments()?.getLong(Routes.ARG_ELAPSED_MILLIS))
        assertEquals(30_000L, currentArguments()?.getLong(Routes.ARG_PREVIOUS_BEST_MILLIS))
        composeRule.onNodeWithText("Deadline missed").assertExists()
        composeRule.onNodeWithText("Next Level").assertDoesNotExist()
        UiScreenshot.capture(composeRule, "result-loss")
        click("Retry")
        assertEquals(levelId, currentArguments()?.getString(Routes.ARG_LEVEL_ID))
        click("Back")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())
    }

    @Test
    fun repeatedSettingsNavigationDoesNotDuplicateDestination() {
        click("Settings")
        composeRule.runOnUiThread { NavigationActions(navController).navigateToSettings() }
        composeRule.waitForIdle()
        assertEquals(Routes.HOME, navController.previousBackStackEntry?.destination?.route)
        click("Back")
        assertEquals(Routes.HOME, currentRoute())
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun resultCanReachLevelSelectWhenGameStartedDirectlyFromHome() {
        // Continue bypasses Level Select. Exercise the same action without assuming saved progress.
        composeRule.runOnUiThread { NavigationActions(navController).navigateToGame(levelId) }
        composeRule.waitForIdle()
        finishGame()
        click("Level Select")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())
        click("Back")
        assertEquals(Routes.HOME, currentRoute())
        assertNull(navController.previousBackStackEntry)
    }
}
