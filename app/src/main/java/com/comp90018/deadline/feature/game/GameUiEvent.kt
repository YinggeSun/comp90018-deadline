package com.comp90018.deadline.feature.game

/** User actions the Game screen sends to [GameViewModel]. */
sealed interface GameUiEvent {
    data class TileTapped(val tileId: String) : GameUiEvent
}
