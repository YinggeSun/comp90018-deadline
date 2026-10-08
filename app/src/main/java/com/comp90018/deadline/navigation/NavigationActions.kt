package com.comp90018.deadline.navigation

import androidx.navigation.NavHostController

/**
 * Navigation operations shared by all screens.
 *
 * Screens receive these as plain callbacks so they never touch the
 * [NavHostController] directly.
 */
class NavigationActions(private val navController: NavHostController) {

    fun navigateToLevelSelect() {
        navController.navigate(Routes.LEVEL_SELECT) {
            launchSingleTop = true
        }
    }

    fun navigateToGame(levelId: String) {
        navController.navigate(Routes.game(levelId))
    }

    /** Replaces the finished game so Back from Result does not return to it. */
    fun navigateToResult(levelId: String) {
        navController.navigate(Routes.result(levelId)) {
            popUpTo(Routes.GAME) { inclusive = true }
        }
    }

    fun navigateToLeaderboard() {
        navController.navigate(Routes.LEADERBOARD) {
            launchSingleTop = true
        }
    }

    fun navigateToSettings() {
        navController.navigate(Routes.SETTINGS) {
            launchSingleTop = true
        }
    }

    /** Starts the same level again from the Result screen. */
    fun replayLevel(levelId: String) {
        navController.navigate(Routes.game(levelId)) {
            popUpTo(Routes.RESULT) { inclusive = true }
        }
    }

    /** Returns to Level Select, dropping any game or result screens above it. */
    fun backToLevelSelect() {
        if (!navController.popBackStack(Routes.LEVEL_SELECT, inclusive = false)) {
            navController.navigate(Routes.LEVEL_SELECT) {
                popUpTo(Routes.HOME)
            }
        }
    }

    /** Returns to Home, clearing everything above it. */
    fun backToHome() {
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    fun navigateUp() {
        navController.navigateUp()
    }
}
