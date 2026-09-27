package com.comp90018.deadline.domain.level.model

/**
 * Defines the logical dimensions available for a level layout.
 */
data class LayoutTemplate(
    val rows: Int,
    val columns: Int
) {
    init {
        require(rows > 0) {
            "Layout rows must be positive."
        }
        require(columns > 0) {
            "Layout columns must be positive."
        }
    }
}
