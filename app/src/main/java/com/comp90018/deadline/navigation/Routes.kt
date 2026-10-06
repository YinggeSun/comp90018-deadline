package com.comp90018.deadline.navigation

import android.net.Uri

/**
 * Navigation destinations of the app.
 *
 * Screens that need input receive it as route arguments. Routes pass
 * identifiers plus, for Result, the small summary of the finished game
 * (won or lost, elapsed time), which nothing persists yet. Screens load
 * everything else through their ViewModels.
 */
object Routes {
    const val HOME = "home"
    const val LEVEL_SELECT = "level-select"
    const val LEADERBOARD = "leaderboard"
    const val SETTINGS = "settings"

    const val ARG_LEVEL_ID = "levelId"
    const val ARG_WON = "won"
    const val ARG_ELAPSED_MILLIS = "elapsedMillis"

    const val GAME = "game/{$ARG_LEVEL_ID}"
    const val RESULT = "result/{$ARG_LEVEL_ID}?$ARG_WON={$ARG_WON}&$ARG_ELAPSED_MILLIS={$ARG_ELAPSED_MILLIS}"

    fun game(levelId: String): String = "game/${Uri.encode(levelId)}"

    fun result(
        levelId: String,
        won: Boolean,
        elapsedMillis: Long,
    ): String = "result/${Uri.encode(levelId)}?$ARG_WON=$won&$ARG_ELAPSED_MILLIS=$elapsedMillis"
}
