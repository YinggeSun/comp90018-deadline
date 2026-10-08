package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType

/**
 * Fixed level definitions used for development and testing.
 *
 * Positions follow [TilePosition]: each tile covers 2 x 2 logical units, so
 * neighbouring tiles on one layer are 2 apart and an odd offset on a higher
 * layer staggers a tile by half. Layout sizes are in the same logical units.
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
                        column = 2,
                        layer = 0
                    )
                ),
                Tile(
                    id = "tile_3",
                    type = TileType.DEFAULT,
                    position = TilePosition(
                        row = 2,
                        column = 0,
                        layer = 0
                    )
                )
            )
        ),
        config = LevelConfig(
            layout = LayoutTemplate(
                rows = 4,
                columns = 4
            ),
            tileCount = 3,
            maxLayer = 0,
            tileVariety = 1
        )
    )

    val LEVEL_1 = Level(
        id = "level_1",
        name = "Level 1",
        board = Board(
            tiles = listOf(
                Tile(
                    id = "level_1_book_1",
                    type = TileType.BOOK,
                    position = TilePosition(row = 0, column = 0, layer = 0)
                ),
                Tile(
                    id = "level_1_book_2",
                    type = TileType.BOOK,
                    position = TilePosition(row = 0, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_1_book_3",
                    type = TileType.BOOK,
                    position = TilePosition(row = 0, column = 4, layer = 0)
                ),
                Tile(
                    id = "level_1_coffee_1",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 2, column = 0, layer = 0)
                ),
                Tile(
                    id = "level_1_coffee_2",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 2, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_1_coffee_3",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 2, column = 4, layer = 0)
                )
            )
        ),
        config = LevelConfig(
            layout = LayoutTemplate(
                rows = 4,
                columns = 6
            ),
            tileCount = 6,
            maxLayer = 0,
            tileVariety = 2
        )
    )

    val LEVEL_2 = Level(
        id = "level_2",
        name = "Level 2",
        board = Board(
            tiles = listOf(
                Tile(
                    id = "level_2_book_1",
                    type = TileType.BOOK,
                    position = TilePosition(row = 0, column = 0, layer = 0)
                ),
                Tile(
                    id = "level_2_coffee_1",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 0, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_2_laptop_1",
                    type = TileType.LAPTOP,
                    position = TilePosition(row = 0, column = 4, layer = 0)
                ),

                Tile(
                    id = "level_2_book_2",
                    type = TileType.BOOK,
                    position = TilePosition(row = 2, column = 0, layer = 0)
                ),
                Tile(
                    id = "level_2_coffee_2",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 2, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_2_laptop_2",
                    type = TileType.LAPTOP,
                    position = TilePosition(row = 2, column = 4, layer = 0)
                ),

                Tile(
                    id = "level_2_book_3",
                    type = TileType.BOOK,
                    position = TilePosition(row = 4, column = 0, layer = 0)
                ),
                Tile(
                    id = "level_2_coffee_3",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 4, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_2_laptop_3",
                    type = TileType.LAPTOP,
                    position = TilePosition(row = 4, column = 4, layer = 0)
                )
            )
        ),
        config = LevelConfig(
            layout = LayoutTemplate(
                rows = 6,
                columns = 6
            ),
            tileCount = 9,
            maxLayer = 0,
            tileVariety = 3
        )
    )

    val LEVEL_3 = Level(
        id = "level_3",
        name = "Level 3",
        board = Board(
            tiles = listOf(
                // Bottom layer
                Tile(
                    id = "level_3_book_1",
                    type = TileType.BOOK,
                    position = TilePosition(row = 0, column = 0, layer = 0)
                ),
                Tile(
                    id = "level_3_book_2",
                    type = TileType.BOOK,
                    position = TilePosition(row = 0, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_3_book_3",
                    type = TileType.BOOK,
                    position = TilePosition(row = 2, column = 0, layer = 0)
                ),

                Tile(
                    id = "level_3_coffee_1",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 2, column = 2, layer = 0)
                ),
                Tile(
                    id = "level_3_coffee_2",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 0, column = 4, layer = 0)
                ),
                Tile(
                    id = "level_3_coffee_3",
                    type = TileType.COFFEE,
                    position = TilePosition(row = 2, column = 4, layer = 0)
                ),

                // Top layer
                Tile(
                    id = "level_3_laptop_1",
                    type = TileType.LAPTOP,
                    position = TilePosition(row = 0, column = 1, layer = 1)
                ),
                Tile(
                    id = "level_3_laptop_2",
                    type = TileType.LAPTOP,
                    position = TilePosition(row = 1, column = 3, layer = 1)
                ),
                Tile(
                    id = "level_3_laptop_3",
                    type = TileType.LAPTOP,
                    position = TilePosition(row = 3, column = 1, layer = 1)
                )
            )
        ),
        config = LevelConfig(
            layout = LayoutTemplate(
                rows = 5,
                columns = 6
            ),
            tileCount = 9,
            maxLayer = 1,
            tileVariety = 3
        )
    )

    val ALL_LEVELS = listOf(
        LEVEL_1,
        LEVEL_2,
        LEVEL_3
    )
}
