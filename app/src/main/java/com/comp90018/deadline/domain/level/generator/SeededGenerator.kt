package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.LevelConfig
import kotlin.random.Random

class SeededGenerator(
    private val seed: Long? = null,
) {
    fun generateTileTypes(config: LevelConfig): List<TileType> {
        val random =
            seed?.let { Random(it) } ?: Random.Default

        val matchableTypes =
            TileType.entries.filter {
                it != TileType.DEFAULT
            }

        require(config.tileVariety <= matchableTypes.size) {
            "Tile variety cannot exceed the number of available matchable tile types."
        }

        val selectedTypes =
            matchableTypes
                .shuffled(random)
                .take(config.tileVariety)

        val tripleCount = config.tileCount / MATCH_SIZE

        val tiles =
            buildList {
                for (type in selectedTypes) {
                    repeat(MATCH_SIZE) {
                        add(type)
                    }
                }

                repeat(tripleCount - selectedTypes.size) {
                    val type = selectedTypes.random(random)

                    repeat(MATCH_SIZE) {
                        add(type)
                    }
                }
            }

        return tiles.shuffled(random)
    }

    companion object {
        const val MATCH_SIZE = 3
    }
}
