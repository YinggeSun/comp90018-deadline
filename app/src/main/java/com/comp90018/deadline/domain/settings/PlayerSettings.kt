package com.comp90018.deadline.domain.settings

/**
 * Player preferences kept on this device. Motion features can be switched off
 * because every essential action is also reachable by tapping.
 * [nickname] is the leaderboard display name; null until the player chooses one.
 */
data class PlayerSettings(
    val nickname: String? = null,
    val hapticsEnabled: Boolean = true,
    val shakeToShuffleEnabled: Boolean = true,
    val tiltToPeekEnabled: Boolean = true
) {
    init {
        require(nickname == null || nickname.isNotBlank()) { "Nickname must not be blank." }
        require(nickname == null || nickname.length <= MAX_NICKNAME_LENGTH) {
            "Nickname must be at most $MAX_NICKNAME_LENGTH characters."
        }
    }

    companion object {
        const val MAX_NICKNAME_LENGTH = 20
    }
}
