package com.comp90018.deadline.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.feature.game.GameRoute
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
    navController: NavHostController = rememberNavController(),
    levelSource: LevelSource? = null,
) {
    // Tests pass their own boards; the app generates a new board each time a level starts.
    val context = LocalContext.current
    val boards =
        remember(levelSource) {
            levelSource ?: (context.applicationContext as DeadlineApp).container.levelSource
        }
    val actions = remember(navController) { NavigationActions(navController) }
    val levelIdArgument =
        listOf(
            navArgument(Routes.ARG_LEVEL_ID) { type = NavType.StringType },
        )
    val resultArguments =
        levelIdArgument +
            listOf(
                navArgument(Routes.ARG_WON) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(Routes.ARG_ELAPSED_MILLIS) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(Routes.ARG_PREVIOUS_BEST_MILLIS) {
                    type = NavType.LongType
                    defaultValue = Routes.NO_PREVIOUS_BEST
                },
            )

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onPlay = actions::navigateToLevelSelect,
                onContinue = actions::navigateToGame,
                onLeaderboard = actions::navigateToLeaderboard,
                onSettings = actions::navigateToSettings,
            )
        }

        composable(Routes.LEVEL_SELECT) {
            LevelSelectScreen(
                onLevelSelected = actions::navigateToGame,
                onBack = actions::navigateUp,
            )
        }

        composable(Routes.GAME, arguments = levelIdArgument) { entry ->
            val levelId = entry.arguments?.getString(Routes.ARG_LEVEL_ID).orEmpty()
            GameRoute(
                levelId = levelId,
                levelSource = boards,
                onGameFinished = { outcome -> actions.navigateToResult(levelId, outcome) },
                onBack = actions::navigateUp,
            )
        }

        composable(Routes.RESULT, arguments = resultArguments) { entry ->
            val arguments = entry.arguments
            val levelId = arguments?.getString(Routes.ARG_LEVEL_ID).orEmpty()
            ResultScreen(
                levelId = levelId,
                won = arguments?.getBoolean(Routes.ARG_WON) ?: false,
                elapsedMillis = arguments?.getLong(Routes.ARG_ELAPSED_MILLIS) ?: 0L,
                previousBestMillis =
                    arguments
                        ?.getLong(Routes.ARG_PREVIOUS_BEST_MILLIS, Routes.NO_PREVIOUS_BEST)
                        ?.takeIf { it != Routes.NO_PREVIOUS_BEST },
                onRetry = { actions.playFromResult(levelId) },
                onNextLevel = actions::playFromResult,
                onLevelSelect = actions::backToLevelSelect,
                onHome = actions::backToHome,
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
