package com.comp90018.deadline.domain.game.model

/**
 * One game tile. The creator assigns an [id] unique within the session.
 * [position] is its logical board location, retained when moved into the tray.
 * Availability is derived from the board by game logic, not stored on the tile.
 */
data class Tile(val id: String, val type: TileType, val position: TilePosition)
