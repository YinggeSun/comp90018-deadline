package com.comp90018.deadline.feature.game.components

import com.comp90018.deadline.R
import com.comp90018.deadline.domain.game.model.TileType

/** Placeholder artwork until tile images are designed. */
internal val TileType.symbol: String
    get() =
        when (this) {
            TileType.DEFAULT -> "📄"
            TileType.BOOK -> "📚"
            TileType.COFFEE -> "☕"
            TileType.LAPTOP -> "💻"
            TileType.ASSIGNMENT -> "📝"
            TileType.QUIZ -> "❓"
            TileType.READING -> "📖"
            TileType.MUSIC -> "\uD83C\uDFB5" // musical note
            TileType.EXAM -> "\uD83D\uDCCB" // clipboard (exam paper)
            TileType.PRESENTATION -> "\uD83C\uDFA4" // microphone
            TileType.LECTURE_SLIDE -> "\uD83D\uDCCA" // bar chart (slide)
        }

/** Spoken name of the tile type, for content descriptions. */
internal val TileType.labelRes: Int
    get() =
        when (this) {
            TileType.DEFAULT -> R.string.game_tile_default
            TileType.BOOK -> R.string.game_tile_book
            TileType.COFFEE -> R.string.game_tile_coffee
            TileType.LAPTOP -> R.string.game_tile_laptop
            TileType.ASSIGNMENT -> R.string.game_tile_assignment
            TileType.QUIZ -> R.string.game_tile_quiz
            TileType.READING -> R.string.game_tile_reading
            TileType.MUSIC -> R.string.game_tile_music
            TileType.EXAM -> R.string.game_tile_exam
            TileType.PRESENTATION -> R.string.game_tile_presentation
            TileType.LECTURE_SLIDE -> R.string.game_tile_lecture_slide
        }
