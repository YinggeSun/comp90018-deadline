package com.comp90018.deadline.domain.game.model

/**
 * Zero-based, non-negative logical board coordinates, never screen pixels.
 * Layer zero is the bottom; higher layers sit above it.
 * Board layout logic defines spacing and overlap.
 */
data class TilePosition(val row: Int, val column: Int, val layer: Int = 0) {
    init {
        require(row >= 0) { "Tile row must be non-negative." }
        require(column >= 0) { "Tile column must be non-negative." }
        require(layer >= 0) { "Tile layer must be non-negative." }
    }
}
