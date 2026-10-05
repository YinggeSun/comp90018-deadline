package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.LayoutTemplate
import com.comp90018.deadline.domain.level.model.LevelConfig
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededGeneratorTest {

    private val config = LevelConfig(
        layout = LayoutTemplate(rows = 4, columns = 4),
        tileCount = 12,
        maxLayer = 1
    )

    @Test
    fun generatedTileCount_matchesConfig() {
        val generator = SeededGenerator(seed = 1234)

        val tiles = generator.generateTileTypes(config)

        assertEquals(config.tileCount, tiles.size)
    }

    @Test
    fun generatedTypeCounts_areCompatibleWithTripleMatching() {
        val generator = SeededGenerator(seed = 1234)

        val tiles = generator.generateTileTypes(config)
        val countsByType = tiles.groupingBy { it }.eachCount()

        assertTrue(
            countsByType.values.all { count ->
                count % SeededGenerator.MATCH_SIZE == 0
            }
        )
    }

    @Test
    fun sameSeed_reproducesSameTileSequence() {
        val first = SeededGenerator(seed = 1234)
            .generateTileTypes(config)

        val second = SeededGenerator(seed = 1234)
            .generateTileTypes(config)

        assertEquals(first, second)
    }

    @Test
    fun generatedTiles_doNotUsePlaceholderDefaultType() {
        val generator = SeededGenerator(seed = 1234)

        val tiles = generator.generateTileTypes(config)

        assertFalse(tiles.contains(TileType.DEFAULT))
    }

    @Test
    fun generatedTileVariety_matchesConfigAcrossSemester() {
        for (week in SemesterDifficulty.weeks) {
            val config = week.levels.single()

            val tiles = SeededGenerator(seed = 42L)
                .generateTileTypes(config)

            assertEquals(
                config.tileVariety,
                tiles.toSet().size
            )

            assertEquals(
                config.tileCount,
                tiles.size
            )

            assertTrue(
                tiles.groupingBy { it }
                    .eachCount()
                    .values
                    .all { it % SeededGenerator.MATCH_SIZE == 0 }
            )

            assertFalse(tiles.contains(TileType.DEFAULT))
        }
    }
}
