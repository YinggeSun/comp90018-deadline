package com.comp90018.deadline.domain.level.model

/**
 * Parameters describing the layout and difficulty constraints of a level.
 */
data class LevelConfig(
    val layout: LayoutTemplate,
    val tileCount: Int,
    val maxLayer: Int,
    val tileVariety: Int = 3,
) {
    init {
        require(tileCount > 0) {
            "Tile count must be positive."
        }

        require(tileCount % 3 == 0) {
            "Tile count must be divisible by 3."
        }

        require(maxLayer >= 0) {
            "Maximum layer must be non-negative."
        }

        require(tileVariety > 0) {
            "Tile variety must be positive."
        }

        require(tileVariety <= tileCount / 3) {
            "Tile variety cannot exceed the number of available triples."
        }
    }
}
