package com.comp90018.deadline.feature.game

/**
 * Summary of a finished game, handed to the Result screen. [previousBestMillis]
 * is the level's Personal Best from before this run, or null if there was none;
 * it is read before the new time is saved, so Result never waits for the save.
 */
data class GameOutcome(
    val won: Boolean,
    val elapsedMillis: Long,
    val previousBestMillis: Long? = null,
)
