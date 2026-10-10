package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.LevelConfig
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import kotlin.math.abs
import kotlin.random.Random

class LevelGenerator(
    private val seed: Long? = null,
) {
    private data class Cell(
        val row: Int,
        val column: Int,
    )

    private data class Region(
        val top: Int,
        val left: Int,
        val height: Int,
        val width: Int,
    ) {
        fun cells(): List<Cell> =
            buildList {
                for (row in top until top + height) {
                    for (column in left until left + width) {
                        add(Cell(row, column))
                    }
                }
            }
    }

    private data class LayerLayout(
        val cells: List<Cell>,
        val horizontalStagger: Boolean,
        val shifts: List<Int>,
        val region: Region,
    ) {
        fun position(
            cell: Cell,
            layer: Int,
        ): TilePosition =
            if (horizontalStagger) {
                TilePosition(
                    row = cell.row * 2,
                    column = cell.column * 2 + shifts[cell.row - region.top],
                    layer = layer,
                )
            } else {
                TilePosition(
                    row = cell.row * 2 + shifts[cell.column - region.left],
                    column = cell.column * 2,
                    layer = layer,
                )
            }

        fun positions(layer: Int): List<TilePosition> = cells.map { position(it, layer) }
    }

    fun generate(
        id: String,
        name: String,
        config: LevelConfig,
        week: Int = SemesterDifficulty.FIRST_WEEK,
    ): Level =
        Level(
            id = id,
            name = name,
            board = generateBoard(config),
            config = config,
            week = week,
        )

    fun generateBoard(config: LevelConfig): Board {
        val rows = config.layout.rows
        val columns = config.layout.columns
        require(rows <= Int.MAX_VALUE / 2 && columns <= Int.MAX_VALUE / 2) {
            "Layout dimensions are too large for tile coordinates."
        }

        val layerCount = minOf(config.maxLayer.toLong() + 1, config.tileCount.toLong()).toInt()
        val capacity = rows.toLong() * columns
        val minimumLargestLayer = (config.tileCount.toLong() + layerCount - 1) / layerCount
        require(minimumLargestLayer <= capacity) {
            "Tile count exceeds the available layout capacity."
        }

        val counts = distributeTiles(config.tileCount, layerCount, capacity)
        val region = compactRegion(rows, columns, counts.first())
        val random = seed?.let { Random(it) } ?: Random.Default
        val horizontalFirst = random.nextBoolean()
        val positions = ArrayList<TilePosition>(config.tileCount)
        var previous: LayerLayout? = null

        for (layer in 0 until layerCount) {
            val layout =
                generateLayer(
                    count = counts[layer],
                    region = region,
                    previous = previous,
                    horizontalStagger = if (layer % 2 == 0) horizontalFirst else !horizontalFirst,
                    random = random,
                )
            positions.addAll(layout.positions(layer))
            previous = layout
        }

        val types = assignTypesAlongWinningOrder(positions, config, random)
        return Board(
            positions.mapIndexed { index, position ->
                Tile(
                    id = "generated_tile_$index",
                    type = types[index],
                    position = position,
                )
            },
        )
    }

    private fun distributeTiles(
        tileCount: Int,
        layerCount: Int,
        capacity: Long,
    ): List<Int> {
        val maxPerLayer = minOf(capacity, tileCount.toLong()).toInt()
        val counts = IntArray(layerCount) { 1 }
        var remaining = tileCount - layerCount

        while (remaining > 0) {
            val next =
                (0 until layerCount)
                    .filter { layer ->
                        counts[layer] < maxPerLayer &&
                            (layer == 0 || counts[layer] < counts[layer - 1])
                    }.maxByOrNull { layer ->
                        (layerCount - layer).toDouble() / (counts[layer] + 0.5)
                    } ?: error("Unable to distribute tiles across layers.")
            counts[next]++
            remaining--
        }
        return counts.toList()
    }

    private fun compactRegion(
        rows: Int,
        columns: Int,
        requiredCells: Int,
    ): Region {
        var height = minOf(rows, 5)
        var width = minOf(columns, 5)

        while (height.toLong() * width < requiredCells) {
            when {
                height < rows && (height <= width || width == columns) -> height++
                width < columns -> width++
                height < rows -> height++
                else -> error("Unable to fit the base layer into the layout.")
            }
        }

        return Region(
            top = (rows - height) / 2,
            left = (columns - width) / 2,
            height = height,
            width = width,
        )
    }

    private fun generateLayer(
        count: Int,
        region: Region,
        previous: LayerLayout?,
        horizontalStagger: Boolean,
        random: Random,
    ): LayerLayout {
        val pattern =
            LayerLayout(
                cells = emptyList(),
                horizontalStagger = horizontalStagger,
                shifts =
                    List(if (horizontalStagger) region.height else region.width) {
                        random.nextInt(2)
                    },
                region = region,
            )
        val lowerPositions = previous?.positions(0).orEmpty()
        val eligible =
            region.cells().filter { cell ->
                previous == null ||
                    lowerPositions.any { lower ->
                        overlapArea(pattern.position(cell, 0), lower) > 0
                    }
            }
        check(eligible.size >= count) { "Not enough supported cells for this layer." }

        val regionRow = region.top + (region.height - 1) / 2.0
        val regionColumn = region.left + (region.width - 1) / 2.0
        val previousRow = previous?.cells?.map { it.row.toDouble() }?.average() ?: regionRow
        val previousColumn = previous?.cells?.map { it.column.toDouble() }?.average() ?: regionColumn
        val targetRow = (regionRow + previousRow) / 2 + random.nextDouble(-0.9, 0.9)
        val targetColumn = (regionColumn + previousColumn) / 2 + random.nextDouble(-0.9, 0.9)

        val selected = linkedSetOf<Cell>()
        val coveredLower = mutableSetOf<Int>()

        repeat(count) {
            val next =
                eligible.asSequence()
                    .filter { it !in selected }
                    .maxByOrNull { candidate ->
                        val neighbours =
                            selected.count { chosen ->
                                abs(chosen.row - candidate.row) +
                                    abs(chosen.column - candidate.column) == 1
                            }
                        val positions = selected + candidate
                        val height = positions.maxOf { it.row } - positions.minOf { it.row } + 1
                        val width = positions.maxOf { it.column } - positions.minOf { it.column } + 1
                        val longThinPenalty =
                            if (positions.size >= 4 && minOf(height, width) == 1) 3.5 else 0.0
                        val tile = pattern.position(candidate, 0)
                        val newlyCovered =
                            lowerPositions.indices.count { index ->
                                index !in coveredLower && overlapArea(tile, lowerPositions[index]) > 0
                            }
                        val overlap = lowerPositions.sumOf { lower -> overlapArea(tile, lower) }
                        val centreDistance =
                            abs(candidate.row - targetRow) + abs(candidate.column - targetColumn)

                        neighbours * 1.5 +
                            newlyCovered * 2.5 +
                            overlap * 0.55 -
                            centreDistance * 0.9 -
                            longThinPenalty +
                            random.nextDouble() * 6.0
                    } ?: error("Unable to complete layer placement.")
            selected.add(next)
            val position = pattern.position(next, 0)
            lowerPositions.forEachIndexed { index, lower ->
                if (overlapArea(position, lower) > 0) coveredLower.add(index)
            }
        }

        return pattern.copy(cells = selected.toList())
    }

    private fun overlapArea(
        a: TilePosition,
        b: TilePosition,
    ): Int {
        val rowOverlap = (2L - abs(a.row.toLong() - b.row)).coerceAtLeast(0L)
        val columnOverlap = (2L - abs(a.column.toLong() - b.column)).coerceAtLeast(0L)
        return (rowOverlap * columnOverlap).toInt()
    }

    private fun assignTypesAlongWinningOrder(
        positions: List<TilePosition>,
        config: LevelConfig,
        random: Random,
    ): List<TileType> {
        val generated = SeededGenerator(seed).generateTileTypes(config)
        val groups =
            generated.groupingBy { it }.eachCount().flatMap { (type, count) ->
                List(count / 3) { type }
            }.shuffled(random)
        check(groups.size * 3 == positions.size) { "Tile types must form complete triples." }

        val parallelGroups =
            when {
                config.maxLayer >= 3 -> 3
                config.maxLayer >= 1 -> 2
                else -> 1
            }
        val removalTypes =
            buildList {
                for (batch in groups.chunked(parallelGroups)) {
                    repeat(3) {
                        addAll(batch)
                    }
                }
            }
        val removalOrder =
            positions.indices.shuffled(random).sortedByDescending { positions[it].layer }
        val assigned = MutableList(positions.size) { TileType.DEFAULT }
        removalOrder.forEachIndexed { index, tileIndex ->
            assigned[tileIndex] = removalTypes[index]
        }
        return assigned
    }
}
