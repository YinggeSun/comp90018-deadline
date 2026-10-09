
package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.abs

/**
 * Regression tests for deterministic, valid and playable
 * procedural levels across all semester weeks.
 */
@RunWith(Parameterized::class)
class SemesterGenerationRegressionTest(
    private val week: Int,
) {
    private fun overlaps(
        a: TilePosition,
        b: TilePosition,
    ): Boolean =
        abs(a.row.toLong() - b.row.toLong()) < 2 &&
            abs(a.column.toLong() - b.column.toLong()) < 2

    @Test
    fun validAndPlayableGeneratedLevel() {
        val config = SemesterDifficulty.forWeek(week).levels.single()

        repeat(100) { index ->
            val seed = index.toLong() * 7919 - 200000
            val context = "week=$week seed=$seed"

            val candidate =
                LevelGenerator(seed).generate(
                    "week_$week",
                    "Week $week",
                    config,
                )

            assertEquals(
                context,
                candidate,
                LevelGenerator(seed).generate(
                    candidate.id,
                    candidate.name,
                    config,
                ),
            )

            val tiles = candidate.board.tiles

            assertEquals(context, config.tileCount, tiles.size)
            assertEquals(
                context,
                tiles.size,
                tiles.map { it.id }.toSet().size,
            )
            assertEquals(
                context,
                tiles.size,
                tiles.map { it.position }.toSet().size,
            )

            assertTrue(
                context,
                tiles.none { it.type == TileType.DEFAULT },
            )

            assertTrue(
                context,
                tiles.groupingBy { it.type }
                    .eachCount()
                    .values
                    .all { it % 3 == 0 },
            )

            tiles.forEach { tile ->
                val position = tile.position

                assertTrue(
                    "$context invalid layer: ${tile.id}",
                    position.layer in 0..config.maxLayer,
                )

                assertTrue(
                    "$context invalid row: ${tile.id}",
                    position.row in 0 until config.layout.rows * 2,
                )

                assertTrue(
                    "$context invalid column: ${tile.id}",
                    position.column in 0 until config.layout.columns * 2,
                )

                assertTrue(
                    "$context same-layer collision: ${tile.id}",
                    tiles.none { other ->
                        other.id != tile.id &&
                            other.position.layer == position.layer &&
                            overlaps(position, other.position)
                    },
                )

                if (position.layer > 0) {
                    assertTrue(
                        "$context unsupported upper tile: ${tile.id}",
                        tiles.any { lower ->
                            lower.position.layer == position.layer - 1 &&
                                overlaps(position, lower.position)
                        },
                    )
                }
            }

            val generator = ValidatedLevelGenerator(seed)

            val result =
                generator.generate(
                    candidate.id,
                    candidate.name,
                    config,
                )

            assertEquals(
                context,
                result,
                generator.generate(
                    candidate.id,
                    candidate.name,
                    config,
                ),
            )

            assertTrue(context, result.attempts in 1..10)

            assertEquals(
                context,
                result.level.board.tiles.size,
                result.winningMoves.size,
            )

            assertEquals(
                context,
                result.winningMoves.size,
                result.winningMoves.toSet().size,
            )

            val engine =
                DefaultGameEngine(
                    result.level,
                    maxUndoDepth = 0,
                    week = week,
                )

            result.winningMoves.forEach { id ->
                assertTrue(
                    "$context illegal move=$id",
                    engine.isTileSelectable(id),
                )
                engine.selectTile(id)
            }

            assertEquals(context, GameStatus.WON, engine.state.status)
            assertTrue(context, engine.state.board.tiles.isEmpty())
            assertTrue(context, engine.state.taskTray.tiles.isEmpty())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "week={0}")
        fun weeks(): List<Array<Int>> = (1..12).map { arrayOf(it) }
    }
}
