package com.comp90018.deadline.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.domain.level.model.FixedLevels
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

    private val levelId = FixedLevels.SAMPLE_LEVEL.id

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
        click("Start sample level")

        assertEquals(Routes.GAME, currentRoute())
        assertEquals(
            levelId,
            navController.currentBackStackEntry?.arguments?.getString(Routes.ARG_LEVEL_ID)
        )
        composeRule.onNodeWithText("Game: $levelId").assertExists()
    }

    @Test
    fun backFromResultSkipsFinishedGame() {
        click("Play")
        click("Start sample level")
        click("Finish game")
        assertEquals(Routes.RESULT, currentRoute())

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()

        assertEquals(Routes.LEVEL_SELECT, currentRoute())
    }

    @Test
    fun resultActionsNavigateToExpectedScreens() {
        click("Play")
        click("Start sample level")
        click("Finish game")

        click("Replay")
        assertEquals(Routes.GAME, currentRoute())

        click("Finish game")
        click("Level Select")
        assertEquals(Routes.LEVEL_SELECT, currentRoute())

        click("Start sample level")
        click("Finish game")
        click("Home")
        assertEquals(Routes.HOME, currentRoute())
        assertNull(navController.previousBackStackEntry)
    }
}
