package com.comp90018.deadline.domain.level.model

import com.comp90018.deadline.domain.level.generator.LevelGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterDifficultyTest {
    @Test
    fun semesterContainsTwelveWeeksAndSixPlayableLevels() {
        assertEquals(12, SemesterDifficulty.SEMESTER_WEEKS)
        assertEquals(6, SemesterDifficulty.TOTAL_LEVELS)

        assertEquals(12, SemesterDifficulty.weeks.size)
        assertEquals(6, SemesterDifficulty.levels.size)

        assertEquals(
            (1..12).toList(),
            SemesterDifficulty.weeks.map { it.week },
        )

        assertEquals(
            listOf(1, 3, 5, 7, 9, 11),
            SemesterDifficulty.levels.map { it.week },
        )
    }

    @Test
    fun eachLevelRepresentsTwoConsecutiveWeeks() {
        for (level in 1..SemesterDifficulty.TOTAL_LEVELS) {
            val firstWeek = SemesterDifficulty.firstWeekForLevel(level)
            val lastWeek = SemesterDifficulty.lastWeekForLevel(level)

            assertEquals(
                (level - 1) * 2 + 1,
                firstWeek,
            )

            assertEquals(
                firstWeek + 1,
                lastWeek,
            )

            assertEquals(
                level,
                SemesterDifficulty.levelForWeek(firstWeek),
            )

            assertEquals(
                level,
                SemesterDifficulty.levelForWeek(lastWeek),
            )
        }
    }

    @Test
    fun bothWeeksShareTheSameLevelConfiguration() {
        for (level in 1..SemesterDifficulty.TOTAL_LEVELS) {
            val firstWeek = SemesterDifficulty.firstWeekForLevel(level)
            val lastWeek = SemesterDifficulty.lastWeekForLevel(level)

            val expectedConfig =
                SemesterDifficulty.forLevel(level).levels.single()

            assertEquals(
                expectedConfig,
                SemesterDifficulty.forWeek(firstWeek).levels.single(),
            )

            assertEquals(
                expectedConfig,
                SemesterDifficulty.forWeek(lastWeek).levels.single(),
            )
        }
    }

    @Test
    fun allSixLevelsHaveExpectedTileCounts() {
        val expectedCounts =
            listOf(18, 27, 36, 45, 54, 63)

        val actualCounts =
            SemesterDifficulty.levels.map {
                it.levels.single().tileCount
            }

        assertEquals(expectedCounts, actualCounts)
    }

    @Test
    fun allSixLevelsHaveExpectedLayerCounts() {
        // maxLayer is zero-indexed.
        // Therefore, actual layer count = maxLayer + 1.

        val expectedLayers =
            listOf(
                2,
                2,
                3,
                4,
                5,
                6,
            )

        val actualLayers =
            SemesterDifficulty.levels.map {
                it.levels.single().maxLayer + 1
            }

        assertEquals(expectedLayers, actualLayers)
    }

    @Test
    fun allSixLevelsHaveExpectedTileVariety() {
        val expectedVarieties =
            listOf(
                3,
                5,
                8,
                8,
                9,
                10,
            )

        val actualVarieties =
            SemesterDifficulty.levels.map {
                it.levels.single().tileVariety
            }

        assertEquals(expectedVarieties, actualVarieties)
    }

    @Test
    fun allSixLevelsHaveExpectedLayoutDimensions() {
        val expectedLayouts =
            listOf(
                4 to 4,
                4 to 4,
                5 to 5,
                5 to 5,
                6 to 6,
                6 to 6,
            )

        val actualLayouts =
            SemesterDifficulty.levels.map {
                val layout = it.levels.single().layout
                layout.rows to layout.columns
            }

        assertEquals(expectedLayouts, actualLayouts)
    }

    @Test
    fun difficultyProgressesAcrossSixLevels() {
        val configs =
            SemesterDifficulty.levels.map {
                it.levels.single()
            }

        for (index in 1 until configs.size) {
            val previous = configs[index - 1]
            val current = configs[index]

            assertTrue(
                "Tile count should increase between levels.",
                current.tileCount > previous.tileCount,
            )

            assertTrue(
                "Maximum layer should not decrease.",
                current.maxLayer >= previous.maxLayer,
            )

            assertTrue(
                "Tile variety should not decrease.",
                current.tileVariety >= previous.tileVariety,
            )
        }
    }

    @Test
    fun generatedLevelRetainsCorrectWeekMetadata() {
        val levelNumber = 4
        val weekConfig = SemesterDifficulty.forLevel(levelNumber)

        val level =
            LevelGenerator(seed = 42L).generate(
                id = "level_4",
                name = "Level 4",
                config = weekConfig.levels.single(),
                week = weekConfig.week,
            )

        assertEquals("level_4", level.id)
        assertEquals("Level 4", level.name)

        assertEquals(7, level.week)
        assertEquals(4, level.levelNumber)

        assertEquals(
            weekConfig.levels.single(),
            level.config,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun weekBelowOneIsRejected() {
        SemesterDifficulty.forWeek(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weekAboveTwelveIsRejected() {
        SemesterDifficulty.forWeek(13)
    }

    @Test(expected = IllegalArgumentException::class)
    fun levelBelowOneIsRejected() {
        SemesterDifficulty.forLevel(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun levelAboveSixIsRejected() {
        SemesterDifficulty.forLevel(7)
    }
}
