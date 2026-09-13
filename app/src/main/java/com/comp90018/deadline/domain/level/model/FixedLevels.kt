package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType

/**
 * Fixed level definitions used for development and testing.
 */
object FixedLevels {

    val SAMPLE_LEVEL = Level(
        id = "sample_level",
        name = "Sample Level",
        board = Board(
            tiles = listOf(
                Tile(
                    id = "tile_1",
                    type = TileType.DEFAULT,
                    position = TilePosition(
                        row = 0,
                        column = 0,
                        layer = 0
                    )
                ),
                Tile(
                    id = "tile_2",
                    type = TileType.DEFAULT,
                    position = TilePosition(
                        row = 0,
                        column = 1,
                        layer = 0
                    )
                ),
                Tile(
                    id = "tile_3",
                    type = TileType.DEFAULT,
                    position = TilePosition(
                        row = 1,
                        column = 0,
                        layer = 0
                    )
                )
            )
        ),
        config = LevelConfig(
            layout = LayoutTemplate(
                rows = 2,
                columns = 2
            ),
            tileCount = 3,
            maxLayer = 0
        )
    )
}
