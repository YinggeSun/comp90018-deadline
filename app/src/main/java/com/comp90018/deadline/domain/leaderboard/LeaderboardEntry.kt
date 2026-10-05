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
 * Why the leaderboard could not be read or written. The UI picks its own wording for each,
 * so no implementation or Firebase message ever reaches the player.
 * A lost connection is not a failure: see [LeaderboardState.Offline] and [SubmitResult.Queued].
 */
enum class LeaderboardFailure {
    /** No anonymous identity yet, so the player cannot be ranked. */
    NOT_SIGNED_IN,

    /** The server refused the request, for example an implausibly fast time (#51). */
    REJECTED,

    /** Anything else; the UI offers Retry. */
    UNKNOWN
}

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

    data class Error(val reason: LeaderboardFailure) : LeaderboardState
}

/** Outcome of submitting a completion time. */
sealed interface SubmitResult {
    /** Stored on the server. */
    data object Submitted : SubmitResult

    /** No connection; the write is kept and uploaded once the device is back online. */
    data object Queued : SubmitResult

    /** Not faster than this player's existing entry, so nothing was written. */
    data object NotFaster : SubmitResult

    data class Failed(val reason: LeaderboardFailure) : SubmitResult
}
