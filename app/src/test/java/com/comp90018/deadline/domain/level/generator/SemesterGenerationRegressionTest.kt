package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Each semester week is reported separately, with reproducible signed seeds. */
@RunWith(Parameterized::class)
class SemesterGenerationRegressionTest(private val week: Int) {
    @Test
    fun hundredSeedsReproduceValidCandidatesAndPlayablePipelineResults() {
        val config = SemesterDifficulty.forWeek(week).levels.single()
        repeat(100) { index ->
            val seed = index.toLong() * 7919 - 200000
            val context = "week=$week seed=$seed"
            val candidate = LevelGenerator(seed).generate("week_$week", "Week $week", config)
            assertEquals(context, candidate, LevelGenerator(seed).generate(candidate.id, candidate.name, config))
            assertEquals(context, config.tileCount, candidate.board.tiles.size)
            assertEquals(context, config.tileCount, candidate.board.tiles.map { it.id }.toSet().size)
            assertEquals(context, config.tileCount, candidate.board.tiles.map { it.position }.toSet().size)
            assertTrue(context, candidate.board.tiles.none { it.type == TileType.DEFAULT })
            assertTrue(context, candidate.board.tiles.groupingBy { it.type }.eachCount().values.all { it % 3 == 0 })
            candidate.board.tiles.forEach { tile ->
                val offset = tile.position.layer % 2
                assertTrue(context, tile.position.layer in 0..config.maxLayer)
                assertEquals(context, offset, tile.position.row % 2)
                assertEquals(context, offset, tile.position.column % 2)
                assertTrue(context, tile.position.row / 2 < config.layout.rows)
                assertTrue(context, tile.position.column / 2 < config.layout.columns)
            }

            val generator = ValidatedLevelGenerator(seed)
            val result = generator.generate(candidate.id, candidate.name, config)
            assertEquals(context, result, generator.generate(candidate.id, candidate.name, config))
            assertTrue(context, result.attempts in 1..10)
            assertEquals(context, result.level.board.tiles.size, result.winningMoves.size)
            assertEquals(context, result.winningMoves.size, result.winningMoves.toSet().size)
            val engine = DefaultGameEngine(result.level, maxUndoDepth = 0, week = week)
            result.winningMoves.forEach { id ->
                assertTrue("$context illegal move=$id", engine.isTileSelectable(id))
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
