package com.comp90018.deadline.feature.game

/** User actions the Game screen sends to [GameViewModel]. */
sealed interface GameUiEvent {
    data class TileTapped(val tileId: String) : GameUiEvent
<<<<<<< HEAD
    data object UndoClicked : GameUiEvent
    data object RestartClicked : GameUiEvent
=======
>>>>>>> 0519c7046b504757bad7e97db2940c35c98277b4
}
