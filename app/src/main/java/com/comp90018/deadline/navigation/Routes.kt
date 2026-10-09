package com.comp90018.deadline.navigation

import android.net.Uri

/**
 * Navigation destinations of the app.
 *
 * Screens that need input receive it as route arguments. Routes pass
 * identifiers plus, for Result, the small summary of the finished game
 * (won or lost, elapsed time, Personal Best before the run). That summary
 * belongs to one finished run, while the saved progress it would otherwise
 * be read from is updated in the background after the Game screen closes.
 * Screens load everything else through their ViewModels.
 */
object Routes {
    const val HOME = "home"
    const val LEVEL_SELECT = "level-select"
    const val LEADERBOARD = "leaderboard"
    const val SETTINGS = "settings"

    const val ARG_LEVEL_ID = "levelId"
    const val ARG_WON = "won"
    const val ARG_ELAPSED_MILLIS = "elapsedMillis"
    const val ARG_PREVIOUS_BEST_MILLIS = "previousBestMillis"

    /** Route value for "no previous Personal Best"; navigation arguments cannot be null longs. */
    const val NO_PREVIOUS_BEST = -1L

    const val GAME = "game/{$ARG_LEVEL_ID}"
    const val RESULT =
        "result/{$ARG_LEVEL_ID}?$ARG_WON={$ARG_WON}&$ARG_ELAPSED_MILLIS={$ARG_ELAPSED_MILLIS}" +
            "&$ARG_PREVIOUS_BEST_MILLIS={$ARG_PREVIOUS_BEST_MILLIS}"

    fun game(levelId: String): String = "game/${Uri.encode(levelId)}"

    fun result(
        levelId: String,
        won: Boolean,
        elapsedMillis: Long,
        previousBestMillis: Long?,
    ): String =
        "result/${Uri.encode(levelId)}?$ARG_WON=$won&$ARG_ELAPSED_MILLIS=$elapsedMillis" +
            "&$ARG_PREVIOUS_BEST_MILLIS=${previousBestMillis ?: NO_PREVIOUS_BEST}"
}
