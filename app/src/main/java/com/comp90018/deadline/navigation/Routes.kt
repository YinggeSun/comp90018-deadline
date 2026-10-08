package com.comp90018.deadline.navigation

import android.net.Uri

/**
 * Navigation destinations of the app.
 *
 * Screens that need input receive it as route arguments. Only identifiers
 * are passed here; screens load everything else through their ViewModels.
 */
object Routes {
    const val HOME = "home"
    const val LEVEL_SELECT = "level-select"
    const val LEADERBOARD = "leaderboard"
    const val SETTINGS = "settings"

    const val ARG_LEVEL_ID = "levelId"

    const val GAME = "game/{$ARG_LEVEL_ID}"
    const val RESULT = "result/{$ARG_LEVEL_ID}"

    fun game(levelId: String): String = "game/${Uri.encode(levelId)}"

    fun result(levelId: String): String = "result/${Uri.encode(levelId)}"
}
