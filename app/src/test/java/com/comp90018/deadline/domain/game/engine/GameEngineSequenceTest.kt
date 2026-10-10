package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.game.shuffle.BoardShuffler
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.abs
import kotlin.random.Random

/** Public API regression: no reflection or production overlap/stress helpers in the oracle. */
@RunWith(Parameterized::class)
class GameEngineSequenceTest(private val seed: Int) {
    @Test
    fun mixedSelectionsUndoShuffleAndRestartAgreeWithReference() {
        val random = Random(seed)
        val tiles =
            List(24) { index ->
                Tile(
                    id = "tile_$index",
                    type = TileType.entries[index % 4],
                    position = TilePosition(index % 2, (index % 6) * 2, index / 6),
                )
            }
        val depth = listOf(0, 1, 3, Int.MAX_VALUE)[seed % 4]
        val engine =
            DefaultGameEngine(
                level = FixedLevels.LEVEL_1.copy(board = Board(tiles)),
                maxUndoDepth = depth,
                boardShuffler = BoardShuffler(Random(seed + 1000)),
                week = 3,
                initialStress = 50,
                stressConfig =
                    StressConfig(
                        maximum = 60,
                        coffeeRecoveryBase = 17,
                        coffeeRecoveryDeclinePerWeek = 3,
                    ),
            )
        val initial = GameState(board = Board(tiles), stress = 50)
        var expected = initial
        val history = ArrayDeque<GameState>()
        val exposedSnapshots = mutableListOf<Pair<GameState, GameState>>()
        val ids = tiles.map { it.id } + "missing"

        fun selectable(
            state: GameState,
            id: String,
        ): Boolean {
            if (state.status != GameStatus.RUNNING) return false
            val tile = state.board.tiles.find { it.id == id } ?: return false
            return state.board.tiles.none { other ->
                other.position.layer > tile.position.layer &&
                    abs(other.position.row.toLong() - tile.position.row) < 2 &&
                    abs(other.position.column.toLong() - tile.position.column) < 2
            }
        }

        repeat(200) { step ->
            val before = expected
            val exposedBefore = engine.state
            val preservedBefore =
                exposedBefore.copy(
                    board = Board(exposedBefore.board.tiles.toList()),
                    taskTray = exposedBefore.taskTray.copy(tiles = exposedBefore.taskTray.tiles.toList()),
                )
            exposedSnapshots.add(exposedBefore to preservedBefore)
            when (random.nextInt(10)) {
                in 0..5 -> {
                    val available = ids.filter { selectable(expected, it) }
                    val id =
                        if (available.isNotEmpty() && random.nextBoolean()) {
                            available.random(random)
                        } else {
                            ids.random(random)
                        }
                    if (selectable(expected, id) && !expected.taskTray.isFull) {
                        val tile = expected.board.tiles.single { it.id == id }
                        val appended = expected.taskTray.tiles + tile
                        val match = appended.count { it.type == tile.type } == 3
                        val tray =
                            expected.taskTray.copy(
                                tiles = if (match) appended.filterNot { it.type == tile.type } else appended,
                            )
                        val board = Board(expected.board.tiles.filterNot { it.id == id })
                        expected =
                            expected.copy(
                                board = board,
                                taskTray = tray,
                                stress =
                                    (expected.stress - if (match && tile.type == TileType.COFFEE) 11 else 0)
                                        .coerceAtLeast(0),
                                status =
                                    when {
                                        board.tiles.isEmpty() -> GameStatus.WON
                                        tray.isFull -> GameStatus.LOST
                                        else -> GameStatus.RUNNING
                                    },
                            )
                        if (match) {
                            history.clear()
                        } else if (depth > 0) {
                            if (history.size == depth) history.removeFirst()
                            history.addLast(before)
                        }
                    }
                    engine.selectTile(id)
                }
                6, 7 -> {
                    if (expected.status == GameStatus.RUNNING && history.isNotEmpty()) {
                        // Undo keeps stress: it follows play time, not moves.
                        expected = history.removeLast().copy(stress = expected.stress)
                    }
                    engine.undo()
                }
                8 -> {
                    engine.shuffle()
                    if (expected.status == GameStatus.RUNNING && expected.board.tiles.size >= 2) {
                        val shuffled = engine.state.board
                        assertEquals(
                            before.board.tiles.map { it.id to it.position },
                            shuffled.tiles.map { it.id to it.position },
                        )
                        assertEquals(
                            before.board.tiles.groupingBy { it.type }.eachCount(),
                            shuffled.tiles.groupingBy { it.type }.eachCount(),
                        )
                        if (shuffled != before.board) history.clear()
                        expected = expected.copy(board = shuffled)
                    }
                }
                else -> {
                    engine.restart()
                    expected = initial
                    history.clear()
                }
            }
            val context = "seed=$seed step=$step depth=$depth"
            assertEquals(context, expected, engine.state)
            exposedSnapshots.forEachIndexed { snapshotStep, (exposed, preserved) ->
                assertEquals("Engine snapshot from step=$snapshotStep mutated: $context", preserved, exposed)
            }
            assertEquals(context, expected.status == GameStatus.RUNNING && history.isNotEmpty(), engine.canUndo)
            ids.forEach { id ->
                assertEquals("$context id=$id", selectable(expected, id), engine.isTileSelectable(id))
            }
            val active = engine.state.board.tiles + engine.state.taskTray.tiles
            assertEquals(context, active.size, active.map { it.id }.toSet().size)
            assertEquals("Initial snapshot mutated: $context", tiles, initial.board.tiles)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "seed={0}")
        fun seeds(): List<Array<Int>> = (0 until 40).map { arrayOf(it) }
    }
}
