package com.comp90018.deadline.domain.game.model

/** Remaining board tiles. Supply a list that will not be mutated after construction. */
data class Board(val tiles: List<Tile> = emptyList())
