package com.comp90018.deadline.domain.game.model

/** Session status; gameplay logic decides when a running game ends. */
enum class GameStatus {
    RUNNING,
    WON,
    LOST
}
