package com.comp90018.deadline.feature.game.components

import com.comp90018.deadline.R
import com.comp90018.deadline.domain.game.model.TileType

/** Placeholder artwork until tile images are designed. */
internal val TileType.symbol: String
    get() = when (this) {
        TileType.DEFAULT -> "📄" // page
        TileType.BOOK -> "📚" // books
        TileType.COFFEE -> "☕" // coffee
        TileType.LAPTOP -> "💻" // laptop
    }

/** Spoken name of the tile type, for content descriptions. */
internal val TileType.labelRes: Int
    get() = when (this) {
        TileType.DEFAULT -> R.string.game_tile_default
        TileType.BOOK -> R.string.game_tile_book
        TileType.COFFEE -> R.string.game_tile_coffee
        TileType.LAPTOP -> R.string.game_tile_laptop
    }
