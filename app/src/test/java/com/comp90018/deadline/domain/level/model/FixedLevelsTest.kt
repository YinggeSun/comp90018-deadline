package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FixedLevelsTest {

    @Test
    fun sampleLevel_hasExpectedConfiguration() {
        val level = FixedLevels.SAMPLE_LEVEL

        assertEquals("sample_level", level.id)
        assertEquals("Sample Level", level.name)
        assertEquals(3, level.board.tiles.size)

        assertEquals(2, level.config.layout.rows)
        assertEquals(2, level.config.layout.columns)
        assertEquals(3, level.config.tileCount)
        assertEquals(0, level.config.maxLayer)
    }

    @Test
    fun fixedLevels_canBeCompletedThroughNormalGameplay() {
        val winningOrders = mapOf(
            FixedLevels.LEVEL_1 to listOf(
                "level_1_book_1",
                "level_1_book_2",
                "level_1_book_3",
                "level_1_coffee_1",
                "level_1_coffee_2",
                "level_1_coffee_3"
            ),
            FixedLevels.LEVEL_2 to listOf(
                "level_2_book_1",
                "level_2_book_2",
                "level_2_book_3",
                "level_2_coffee_1",
                "level_2_coffee_2",
                "level_2_coffee_3",
                "level_2_laptop_1",
                "level_2_laptop_2",
                "level_2_laptop_3"
            ),
            FixedLevels.LEVEL_3 to listOf(
                "level_3_laptop_1",
                "level_3_laptop_2",
                "level_3_laptop_3",
                "level_3_book_1",
                "level_3_book_2",
                "level_3_book_3",
                "level_3_coffee_1",
                "level_3_coffee_2",
                "level_3_coffee_3"
            )
        )

        for ((level, winningOrder) in winningOrders) {
            val engine = DefaultGameEngine(level)

            for (tileId in winningOrder) {
                engine.selectTile(tileId)
            }

            assertEquals(
                "${level.name} should be completable",
                GameStatus.WON,
                engine.state.status
            )
        }
    }
}
