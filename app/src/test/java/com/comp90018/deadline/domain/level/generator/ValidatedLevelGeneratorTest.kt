package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.model.*
import com.comp90018.deadline.domain.level.solver.*
import org.junit.Assert.*
import org.junit.Test

class ValidatedLevelGeneratorTest {
    private val config =
        SemesterDifficulty
            .forWeek(12)
            .levels
            .single()

    private fun assertPlayable(result: LevelGenerationResult) {
        val engine = DefaultGameEngine(result.level)

        for (id in result.winningMoves) {
            assertTrue(
                "Solution contains an unavailable tile: $id",
                engine.isTileSelectable(id),
            )
            engine.selectTile(id)
        }

        assertEquals(GameStatus.WON, engine.state.status)
        assertTrue(engine.state.board.tiles.isEmpty())
        assertTrue(engine.state.taskTray.tiles.isEmpty())
    }

    @Test
    fun generatedPipelineReturnsPlayableLevelsAcrossSampledWeeksAndSeeds() {
        val maxAttempts = 10

        for (week in SemesterDifficulty.weeks) {
            repeat(10) { seed ->
                val weekConfig = week.levels.single()
                val requestedId = "week_${week.week}"
                val requestedName = "Week ${week.week}"

                val result =
                    ValidatedLevelGenerator(
                        seed = seed.toLong(),
                        maxAttempts = maxAttempts,
                    ).generate(
                        id = requestedId,
                        name = requestedName,
                        config = weekConfig,
                    )

                assertTrue(result.attempts in 1..maxAttempts)

                if (result.usedFallback) {
                    assertEquals(FixedLevels.LEVEL_1, result.level)
                    assertNull(result.candidateSeed)
                } else {
                    assertEquals(requestedId, result.level.id)
                    assertEquals(requestedName, result.level.name)
                    assertEquals(weekConfig, result.level.config)
                    assertNotNull(result.candidateSeed)
                }

                assertPlayable(result)
            }
        }
    }

    @Test
    fun knownSolvableConfigurationReturnsGeneratedLevelWithoutFallback() {
        // Three tiles form one matching triple.
        val simpleConfig =
            LevelConfig(
                layout = LayoutTemplate(rows = 1, columns = 3),
                tileCount = 3,
                maxLayer = 0,
                tileVariety = 1,
            )

        val result =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 10,
            ).generate(
                id = "simple_generated",
                name = "Simple Generated Level",
                config = simpleConfig,
            )

        assertFalse(result.usedFallback)
        assertEquals(1, result.attempts)
        assertEquals(42L, result.candidateSeed)

        assertEquals("simple_generated", result.level.id)
        assertEquals("Simple Generated Level", result.level.name)
        assertEquals(simpleConfig, result.level.config)

        assertPlayable(result)
    }

    @Test
    fun retriesEveryUnacceptedResultAndStopsAtFirstSolution() {
        val seeds = mutableListOf<Long>()
        var calls = 0

        val generator =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 5,
                createCandidate = { id, name, cfg, seed ->
                    seeds.add(seed)
                    LevelGenerator(seed).generate(id, name, cfg)
                },
                validateCandidate = { level ->
                    when (calls++) {
                        0 -> SolvabilityResult.Unsolvable
                        1 -> SolvabilityResult.SearchLimitReached
                        2 -> SolvabilityResult.InvalidBoard("Test rejection")
                        else -> SolvabilityValidator().validate(level)
                    }
                },
            )

        val result = generator.generate("test", "Test", config)

        assertEquals(listOf(42L, 43L, 44L, 45L), seeds)
        assertEquals(4, calls)
        assertEquals(4, result.attempts)
        assertEquals(45L, result.candidateSeed)
        assertFalse(result.usedFallback)

        assertPlayable(result)
    }

    @Test
    fun exhaustedRetriesReturnVerifiedFallback() {
        val rejections =
            listOf(
                SolvabilityResult.Unsolvable,
                SolvabilityResult.SearchLimitReached,
                SolvabilityResult.InvalidBoard("Bad board"),
            )

        for (rejection in rejections) {
            var calls = 0

            val generator =
                ValidatedLevelGenerator(
                    seed = 1L,
                    maxAttempts = 3,
                    createCandidate = { id, name, cfg, seed ->
                        LevelGenerator(seed).generate(id, name, cfg)
                    },
                    validateCandidate = {
                        calls++
                        rejection
                    },
                )

            val result =
                generator.generate(
                    id = "week_12",
                    name = "Week 12",
                    config = config,
                )

            assertEquals(3, calls)
            assertEquals(3, result.attempts)
            assertTrue(result.usedFallback)
            assertNull(result.candidateSeed)

            // Fallback keeps its own identity and configuration.
            assertEquals(FixedLevels.LEVEL_1, result.level)
            assertNotEquals("week_12", result.level.id)

            assertPlayable(result)
        }
    }

    @Test
    fun realSearchLimitFallsBackSafely() {
        val generator =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 2,
                validator =
                    SolvabilityValidator(
                        BacktrackingSolver(maxVisitedStates = 1),
                    ),
            )

        val result = generator.generate("test", "Test", config)

        assertTrue(result.usedFallback)
        assertEquals(2, result.attempts)
        assertNull(result.candidateSeed)

        assertPlayable(result)
    }

    @Test
    fun impossibleCapacityFallsBackWithoutRepeatingSameError() {
        val impossibleConfig =
            LevelConfig(
                layout = LayoutTemplate(rows = 1, columns = 1),
                tileCount = 3,
                maxLayer = 0,
                tileVariety = 1,
            )

        val result =
            ValidatedLevelGenerator(seed = 42L).generate(
                id = "test",
                name = "Test",
                config = impossibleConfig,
            )

        assertTrue(result.usedFallback)
        assertEquals(1, result.attempts)

        assertPlayable(result)
    }

    @Test
    fun fixedSeedReproducesResultIncludingAfterRetries() {
        val generator = ValidatedLevelGenerator(seed = 42L)

        val first = generator.generate("test", "Test", config)

        assertEquals(
            first,
            generator.generate("test", "Test", config),
        )

        assertEquals(
            first,
            ValidatedLevelGenerator(seed = 42L)
                .generate("test", "Test", config),
        )

        fun retrying(): ValidatedLevelGenerator =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 3,
                createCandidate = { id, name, cfg, seed ->
                    LevelGenerator(seed).generate(id, name, cfg)
                },
                validateCandidate = {
                    SolvabilityResult.SearchLimitReached
                },
            )

        assertEquals(
            retrying().generate("test", "Test", config),
            retrying().generate("test", "Test", config),
        )
    }

    @Test
    fun mismatchedCandidateCannotBeServed() {
        val generator =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 2,
                createCandidate = { _, _, _, _ ->
                    FixedLevels.LEVEL_1
                },
                validateCandidate = {
                    error("Mismatched candidate should not reach solver")
                },
            )

        val result = generator.generate("test", "Test", config)

        assertTrue(result.usedFallback)
        assertEquals(2, result.attempts)

        assertPlayable(result)
    }

    @Test
    fun rejectsInvalidRetryBudgets() {
        for (budget in listOf(-1, 0, 101)) {
            assertThrows(IllegalArgumentException::class.java) {
                ValidatedLevelGenerator(maxAttempts = budget)
            }
        }
    }

    @Test
    fun sixBiweeklyLevelsHaveCorrectMetadataBeforeValidation() {
        for (levelNumber in 1..SemesterDifficulty.TOTAL_LEVELS) {
            val expected = SemesterDifficulty.forLevel(levelNumber)
            val expectedWeek = expected.week
            val observedWeeks = mutableListOf<Int>()
            val validator = SolvabilityValidator()

            val generator =
                ValidatedLevelGenerator(
                    seed = 42L,
                    maxAttempts = 10,
                    createCandidate = { id, name, config, seed ->
                        LevelGenerator(seed).generate(id, name, config)
                    },
                    validateCandidate = { level ->
                        observedWeeks.add(level.week)
                        validator.validate(level)
                    },
                )

            val result = generator.generateForLevel(levelNumber)

            assertTrue(
                "Level $levelNumber should reach validation",
                observedWeeks.isNotEmpty(),
            )

            assertTrue(
                "Level $levelNumber was validated with the wrong week",
                observedWeeks.all { it == expectedWeek },
            )

            if (result.usedFallback) {
                assertEquals(FixedLevels.LEVEL_1, result.level)
                assertNull(result.candidateSeed)
            } else {
                assertEquals("level_$levelNumber", result.level.id)
                assertEquals("Level $levelNumber", result.level.name)
                assertEquals(expectedWeek, result.level.week)
                assertEquals(levelNumber, result.level.levelNumber)
                assertEquals(expected.levels.single(), result.level.config)
                assertNotNull(result.candidateSeed)
            }

            assertPlayable(result)
        }
    }

    @Test
    fun firstBiweeklyLevelGeneratesWithoutFallback() {
        val result =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 10,
            ).generateForLevel(1)

        assertFalse(
            "Level 1 should be procedurally generated",
            result.usedFallback,
        )

        assertEquals("level_1", result.level.id)
        assertEquals(1, result.level.week)
        assertEquals(1, result.level.levelNumber)
        assertEquals(18, result.level.board.tiles.size)
        assertEquals(1, result.level.config.maxLayer)
        assertNotNull(result.candidateSeed)

        assertPlayable(result)
    }

    @Test
    fun biweeklyFallbackCannotMasqueradeAsLevelSix() {
        val generator =
            ValidatedLevelGenerator(
                seed = 42L,
                maxAttempts = 2,
                createCandidate = { id, name, config, seed ->
                    LevelGenerator(seed).generate(id, name, config)
                },
                validateCandidate = {
                    SolvabilityResult.SearchLimitReached
                },
            )

        val result = generator.generateForLevel(6)

        assertTrue(result.usedFallback)
        assertEquals(2, result.attempts)
        assertNull(result.candidateSeed)

        assertEquals(FixedLevels.LEVEL_1, result.level)
        assertNotEquals("level_6", result.level.id)
        assertNotEquals(11, result.level.week)
        assertNotEquals(
            SemesterDifficulty.forLevel(6).levels.single(),
            result.level.config,
        )

        assertPlayable(result)
    }

    @Test
    fun invalidBiweeklyLevelNumbersAreRejected() {
        for (levelNumber in listOf(0, 7, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                ValidatedLevelGenerator(seed = 42L)
                    .generateForLevel(levelNumber)
            }
        }
    }
}
