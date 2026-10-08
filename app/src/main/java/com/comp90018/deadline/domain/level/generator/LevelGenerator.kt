package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.LevelConfig
import kotlin.random.Random

/**
 * Creates candidate levels; solvability validation is a separate responsibility.
 * For generation, layout rows/columns count tile slots, not half-tile coordinates.
 * Slots are two logical units apart, with alternate layers offset by one unit.
 */
class LevelGenerator(private val seed: Long? = null) {

    fun generate(id: String, name: String, config: LevelConfig): Level =
        Level(id, name, generateBoard(config), config)

    fun generateBoard(config: LevelConfig): Board {
        val rows = config.layout.rows
        val columns = config.layout.columns
        require(rows <= Int.MAX_VALUE / 2 && columns <= Int.MAX_VALUE / 2) {
            "Layout dimensions are too large for tile coordinates."
        }

        // maxLayer is an inclusive upper bound, not a layer count.
        val layerCount = minOf(config.maxLayer.toLong() + 1, config.tileCount.toLong()).toInt()
        val cellsPerLayer = rows.toLong() * columns
        val largestLayer = (config.tileCount.toLong() + layerCount - 1) / layerCount
        require(largestLayer <= cellsPerLayer) {
            "Tile count exceeds the available layout capacity."
        }

        val random = seed?.let { Random(it) } ?: Random.Default
        // Sample without replacement without allocating the entire grid.
        val swaps = mutableMapOf<Long, Long>()
        val cells = List(largestLayer.toInt()) { index ->
            val remaining = cellsPerLayer - index
            val picked = random.nextLong(remaining)
            val cell = swaps[picked] ?: picked
            val last = remaining - 1
            swaps[picked] = swaps[last] ?: last
            swaps.remove(last)
            cell
        }

        val positions = buildList {
            repeat(layerCount) { layer ->
                val count = config.tileCount / layerCount +
                    if (layer < config.tileCount % layerCount) 1 else 0
                val offset = layer % 2
                // Nested prefixes ensure every upper tile overlaps a tile below it.
                repeat(count) { index ->
                    val cell = cells[index]
                    add(TilePosition(
                        row = (cell / columns).toInt() * 2 + offset,
                        column = (cell % columns).toInt() * 2 + offset,
                        layer = layer
                    ))
                }
            }
        }

        val types = SeededGenerator(seed).generateTileTypes(config)
        return Board(positions.mapIndexed { index, position ->
            Tile(id = "generated_tile_$index", type = types[index], position = position)
        })
    }
}
