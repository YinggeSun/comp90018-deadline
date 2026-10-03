package com.comp90018.deadline.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.tileTestTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            navController = TestNavHostController(LocalContext.current).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
            }
            AppNavHost(navController = navController)
        }
    }

    private fun currentRoute() = navController.currentBackStackEntry?.destination?.route

    private fun click(label: String) {
        composeRule.onNodeWithText(label).performClick()
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
            navController.currentBackStackEntry?.arguments?.getString(Routes.ARG_LEVEL_ID)
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

        click("Replay")
        assertEquals(Routes.GAME, currentRoute())

        finishGame()
        click("Level Select")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())

        click(level.name)
        finishGame()
        click("Home")
        assertEquals(Routes.HOME, currentRoute())
        assertNull(navController.previousBackStackEntry)
    }
}
