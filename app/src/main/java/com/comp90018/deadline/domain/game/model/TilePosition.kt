package com.comp90018.deadline.domain.game.model

/**
 * Zero-based, non-negative logical anchor coordinates, never screen pixels.
 * Each tile occupies [row, row + 2) × [column, column + 2) logical units;
 * one coordinate step permits half-tile staggering. Boundary-only contact is not overlap.
 * Layer zero is the bottom. A strictly higher layer covers a lower tile exactly
 * when their footprints intersect by positive area, even across non-adjacent layers.
 */
data class TilePosition(val row: Int, val column: Int, val layer: Int = 0) {
    init {
        require(row >= 0) { "Tile row must be non-negative." }
        require(column >= 0) { "Tile column must be non-negative." }
        require(layer >= 0) { "Tile layer must be non-negative." }
    }
}
