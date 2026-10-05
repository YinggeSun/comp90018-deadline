package com.comp90018.deadline.domain.leaderboard

/** One player's best submitted time on one level. */
data class LeaderboardEntry(
    val userId: String,
    val nickname: String,
    val levelId: String,
    val timeMillis: Long,
    val submittedAtMillis: Long
)

/**
 * What a leaderboard screen can show. Network trouble is a state, not an exception,
 * so a failure costs the player a leaderboard placing and never a level.
 */
sealed interface LeaderboardState {
    data object Loading : LeaderboardState

    /** [entries] are ranked fastest first. */
    data class Ranked(val entries: List<LeaderboardEntry>) : LeaderboardState

    /** No connection; [cachedEntries] are the last ranking seen, possibly empty. */
    data class Offline(val cachedEntries: List<LeaderboardEntry>) : LeaderboardState

    data class Error(val message: String) : LeaderboardState
}
