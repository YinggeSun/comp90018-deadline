package com.comp90018.deadline.navigation

import androidx.navigation.NavHostController
import com.comp90018.deadline.feature.game.GameOutcome

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
    fun navigateToResult(
        levelId: String,
        outcome: GameOutcome,
    ) {
        navController.navigate(Routes.result(levelId, outcome.won, outcome.elapsedMillis)) {
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

    /**
     * Starts [levelId] from the Result screen: the same level for Retry, or
     * the following one for Next Level. Result is dropped so Back skips it.
     */
    fun playFromResult(levelId: String) {
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
