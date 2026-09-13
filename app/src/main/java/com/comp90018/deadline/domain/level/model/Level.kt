package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.model.Board

/**
 * Complete definition of a playable level.
 *
 * A level contains the initial board layout and the configuration
 * controlling the rules and difficulty of that level.
 */
data class Level(
    val id: String,
    val name: String,
    val board: Board,
    val config: LevelConfig
)
