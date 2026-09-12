package com.comp90018.deadline.domain.game.model

/**
 * Ordered tiles in the player's Task Tray, before or after match resolution.
 * Supply a list that will not be mutated after construction. Fullness alone
 * does not imply a loss: game logic must resolve matches first.
 */
data class TrayState(
    val tiles: List<Tile> = emptyList(),
    val capacity: Int = DEFAULT_CAPACITY
) {
    init {
        require(capacity > 0) { "Tray capacity must be positive." }
        require(tiles.size <= capacity) { "Tray tiles must not exceed capacity." }
    }

    val isFull: Boolean
        get() = tiles.size == capacity

    companion object {
        /** Seven-slot Task Tray specified in README.md. */
        const val DEFAULT_CAPACITY = 7
    }
}
