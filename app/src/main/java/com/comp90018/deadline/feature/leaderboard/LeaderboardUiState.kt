package com.comp90018.deadline.feature.leaderboard

import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure

/** One level the player can pick above the ranking. */
data class LeaderboardLevelTab(
    val id: String,
    val name: String,
)

/** One ranked player. Equal times share a rank, so two players can both be 2nd. */
data class LeaderboardRowUiModel(
    val rank: Int,
    val nickname: String,
    val timeMillis: Long,
    /** True for this player's own entry, which the screen highlights. */
    val isCurrentPlayer: Boolean,
)

/** What the ranking area shows for the selected level. */
sealed interface LeaderboardContent {
    data object Loading : LeaderboardContent

    /** Live ranking from the server, fastest first; may be empty. */
    data class Ranked(val rows: List<LeaderboardRowUiModel>) : LeaderboardContent

    /** No connection; [rows] are the last ranking saved on this phone, possibly empty. */
    data class Offline(val rows: List<LeaderboardRowUiModel>) : LeaderboardContent

    data class Error(val reason: LeaderboardFailure) : LeaderboardContent
}

data class LeaderboardUiState(
    val levels: List<LeaderboardLevelTab> = emptyList(),
    val selectedLevelId: String = "",
    val content: LeaderboardContent = LeaderboardContent.Loading,
)
