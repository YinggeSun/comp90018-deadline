package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedLevelsTest {
    @Test
    fun sampleLevel_hasExpectedConfiguration() {
        val level = FixedLevels.SAMPLE_LEVEL

        assertEquals("sample_level", level.id)
        assertEquals("Sample Level", level.name)
        assertEquals(3, level.board.tiles.size)

        assertEquals(4, level.config.layout.rows)
        assertEquals(4, level.config.layout.columns)
        assertEquals(3, level.config.tileCount)
        assertEquals(0, level.config.maxLayer)
    }

    @Test
    fun fixedLevels_canBeCompletedThroughNormalGameplay() {
        val winningOrders =
            mapOf(
                FixedLevels.LEVEL_1 to
                    listOf(
                        "level_1_book_1",
                        "level_1_book_2",
                        "level_1_book_3",
                        "level_1_coffee_1",
                        "level_1_coffee_2",
                        "level_1_coffee_3",
                    ),
                FixedLevels.LEVEL_2 to
                    listOf(
                        "level_2_book_1",
                        "level_2_book_2",
                        "level_2_book_3",
                        "level_2_coffee_1",
                        "level_2_coffee_2",
                        "level_2_coffee_3",
                        "level_2_laptop_1",
                        "level_2_laptop_2",
                        "level_2_laptop_3",
                    ),
                FixedLevels.LEVEL_3 to
                    listOf(
                        "level_3_laptop_1",
                        "level_3_laptop_2",
                        "level_3_laptop_3",
                        "level_3_book_1",
                        "level_3_book_2",
                        "level_3_book_3",
                        "level_3_coffee_1",
                        "level_3_coffee_2",
                        "level_3_coffee_3",
                    ),
            )

        for ((level, winningOrder) in winningOrders) {
            val engine = DefaultGameEngine(level)

            for (tileId in winningOrder) {
                engine.selectTile(tileId)
            }

            assertEquals(
                "${level.name} should be completable",
                GameStatus.WON,
                engine.state.status,
            )
        }
    }

    private val allLevels = FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL

    @Test
    fun fixedLevels_tilesFitInsideLayout() {
        for (level in allLevels) {
            val layout = level.config.layout
            for (tile in level.board.tiles) {
                assertTrue(
                    "${tile.id} should fit inside ${level.name}'s layout",
                    tile.position.row + TILE_SPAN <= layout.rows &&
                        tile.position.column + TILE_SPAN <= layout.columns,
                )
            }
        }
    }

    @Test
    fun fixedLevels_tilesOnSameLayerDoNotOverlap() {
        for (level in allLevels) {
            val tiles = level.board.tiles
            for ((index, a) in tiles.withIndex()) {
                for (b in tiles.drop(index + 1)) {
                    val overlaps =
                        a.position.layer == b.position.layer &&
                            kotlin.math.abs(a.position.row - b.position.row) < TILE_SPAN &&
                            kotlin.math.abs(a.position.column - b.position.column) < TILE_SPAN
                    assertFalse("${a.id} and ${b.id} overlap in ${level.name}", overlaps)
                }
            }
        }
    }

    private companion object {
        /** Each tile covers 2 x 2 logical units (see TilePosition). */
        const val TILE_SPAN = 2
    }
}
