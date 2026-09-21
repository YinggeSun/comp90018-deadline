package com.comp90018.deadline.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comp90018.deadline.feature.game.GameScreen
import com.comp90018.deadline.feature.home.HomeScreen
import com.comp90018.deadline.feature.leaderboard.LeaderboardScreen
import com.comp90018.deadline.feature.levelselect.LevelSelectScreen
import com.comp90018.deadline.feature.result.ResultScreen
import com.comp90018.deadline.feature.settings.SettingsScreen

/**
 * Root navigation graph.
 *
 * Only wires routes to screens; no game or data logic belongs here.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val actions = remember(navController) { NavigationActions(navController) }
    val levelIdArgument = listOf(
        navArgument(Routes.ARG_LEVEL_ID) { type = NavType.StringType }
    )

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onPlay = actions::navigateToLevelSelect,
                onLeaderboard = actions::navigateToLeaderboard,
                onSettings = actions::navigateToSettings
            )
        }

        composable(Routes.LEVEL_SELECT) {
            LevelSelectScreen(
                onLevelSelected = actions::navigateToGame,
                onBack = actions::navigateUp
            )
        }

        composable(Routes.GAME, arguments = levelIdArgument) { entry ->
            val levelId = entry.arguments?.getString(Routes.ARG_LEVEL_ID).orEmpty()
            GameScreen(
                levelId = levelId,
                onGameFinished = { actions.navigateToResult(levelId) },
                onBack = actions::navigateUp
            )
        }

        composable(Routes.RESULT, arguments = levelIdArgument) { entry ->
            val levelId = entry.arguments?.getString(Routes.ARG_LEVEL_ID).orEmpty()
            ResultScreen(
                levelId = levelId,
                onReplay = { actions.replayLevel(levelId) },
                onLevelSelect = actions::backToLevelSelect,
                onHome = actions::backToHome
            )
        }

        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(onBack = actions::navigateUp)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = actions::navigateUp)
        }
    }
}
