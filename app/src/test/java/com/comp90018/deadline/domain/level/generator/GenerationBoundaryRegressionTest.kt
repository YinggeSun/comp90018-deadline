package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.LayoutTemplate
import com.comp90018.deadline.domain.level.model.LevelConfig
import com.comp90018.deadline.domain.level.solver.SolvabilityResult
import com.comp90018.deadline.domain.level.solver.SolvabilityValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationBoundaryRegressionTest {
    private fun assertPlayable(result: LevelGenerationResult) {
        assertEquals(result.level.board.tiles.size, result.winningMoves.size)
        assertEquals(result.winningMoves.size, result.winningMoves.toSet().size)

        val engine = DefaultGameEngine(result.level, maxUndoDepth = 0)

        result.winningMoves.forEach { id ->
            assertTrue("Illegal move: $id", engine.isTileSelectable(id))
            engine.selectTile(id)
        }

        assertEquals(GameStatus.WON, engine.state.status)
        assertTrue(engine.state.board.tiles.isEmpty())
        assertTrue(engine.state.taskTray.tiles.isEmpty())
    }

    @Test
    fun sparseMaximumSafeCoordinatesAndExtremeSeedsProducePlayableLevels() {
        val config =
            LevelConfig(
                LayoutTemplate(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2),
                3,
                2,
                tileVariety = 1,
            )

        for (seed in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L)) {
            val result = ValidatedLevelGenerator(seed).generate("sparse", "Sparse", config)
            assertFalse(result.usedFallback)
            assertEquals(
                result,
                ValidatedLevelGenerator(seed).generate("sparse", "Sparse", config),
            )
            assertPlayable(result)
        }
    }

    @Test
    fun unsafeCoordinateDimensionsAreRejectedAndPipelineReturnsVerifiedFallback() {
        for (
        layout in
        listOf(
            LayoutTemplate(Int.MAX_VALUE, 1),
            LayoutTemplate(1, Int.MAX_VALUE),
        )
        ) {
            val config =
                LevelConfig(
                    layout,
                    3,
                    2,
                    tileVariety = 1,
                )

            assertThrows(IllegalArgumentException::class.java) {
                LevelGenerator(0).generate("unsafe", "Unsafe", config)
            }

            val result =
                ValidatedLevelGenerator(0).generate(
                    "unsafe",
                    "Unsafe",
                    config,
                )

            assertTrue(result.usedFallback)
            assertEquals(1, result.attempts)
            assertEquals(FixedLevels.LEVEL_1, result.level)
            assertPlayable(result)
        }
    }

    @Test
    fun inclusiveMaximumLayerDoesNotOverflowOrAllocateBillionsOfLayers() {
        val config =
            LevelConfig(
                LayoutTemplate(1, 1),
                3,
                Int.MAX_VALUE,
                tileVariety = 1,
            )

        val result =
            ValidatedLevelGenerator(Long.MIN_VALUE).generate(
                "stack",
                "Stack",
                config,
            )

        assertFalse(result.usedFallback)
        assertEquals(
            listOf(0, 1, 2),
            result.level.board.tiles.map { it.position.layer },
        )
        assertPlayable(result)
    }

    @Test
    fun retryAcrossLongOverflowIsDeterministicAndStopsAfterFirstAcceptedCandidate() {
        val config =
            LevelConfig(
                LayoutTemplate(1, 3),
                3,
                0,
                tileVariety = 1,
            )

        fun generate(): Pair<LevelGenerationResult, List<Long>> {
            val seeds = mutableListOf<Long>()
            var calls = 0

            val generator =
                ValidatedLevelGenerator(
                    seed = Long.MAX_VALUE,
                    maxAttempts = 3,
                    createCandidate = { id, name, cfg, seed ->
                        seeds.add(seed)
                        LevelGenerator(seed).generate(id, name, cfg)
                    },
                    validateCandidate = { level ->
                        if (calls++ == 0) {
                            SolvabilityResult.SearchLimitReached
                        } else {
                            SolvabilityValidator().validate(level)
                        }
                    },
                )

            return generator.generate("retry", "Retry", config) to seeds
        }

        val (result, seeds) = generate()

        assertEquals(listOf(Long.MAX_VALUE, Long.MIN_VALUE), seeds)
        assertEquals(Long.MIN_VALUE, result.candidateSeed)
        assertEquals(2, result.attempts)
        assertFalse(result.usedFallback)
        assertEquals(result to seeds, generate())
        assertPlayable(result)
    }
}
