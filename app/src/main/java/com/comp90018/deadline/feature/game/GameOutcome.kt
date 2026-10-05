package com.comp90018.deadline.feature.game

/** Summary of a finished game, handed to the Result screen. */
data class GameOutcome(val won: Boolean, val elapsedMillis: Long)
