package com.comp90018.deadline.domain.game.model

/**
 * Matchable tile categories used by the game engine.
 *
 * Three tiles of the same type form a match and are removed from the task tray.
 */
enum class TileType {
    DEFAULT,
    BOOK,
    COFFEE,
    LAPTOP
}
