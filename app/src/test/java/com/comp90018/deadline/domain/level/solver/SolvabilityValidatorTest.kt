package com.comp90018.deadline.domain.level.solver

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.*
import com.comp90018.deadline.domain.level.generator.LevelGenerator
import com.comp90018.deadline.domain.level.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SolvabilityValidatorTest {

    private val validator = SolvabilityValidator()

    private val supportedTypes = listOf(
        TileType.BOOK,
        TileType.COFFEE,
        TileType.LAPTOP,
        TileType.ASSIGNMENT,
        TileType.QUIZ,
        TileType.READING,
    )

    // A single stack forces selection in the supplied order, top to bottom.
    private fun stack(types: List<TileType>): Level = Level(
        "stack",
        "Stack",
        Board(
            types.mapIndexed { index, type ->
                Tile(
                    "tile_$index",
                    type,
                    TilePosition(0, 0, types.lastIndex - index)
                )
            }
        ),
        LevelConfig(
            layout = LayoutTemplate(1, 1),
            tileCount = types.size,
            maxLayer = types.lastIndex,
            tileVariety = types.distinct().size
        )
    )

    private fun assertSolutionPlays(level: Level) {
        val result = validator.validate(level)
        assertTrue(
            "Expected a solution, got $result",
            result is SolvabilityResult.Solvable
        )

        val moves = (result as SolvabilityResult.Solvable).moves

        assertEquals(level.board.tiles.size, moves.size)
        assertEquals(moves.size, moves.toSet().size)

        val engine = DefaultGameEngine(level)

        for (id in moves) {
            assertTrue(
                "Illegal move: $id",
                engine.isTileSelectable(id)
            )
            engine.selectTile(id)
        }

        assertEquals(GameStatus.WON, engine.state.status)
        assertTrue(engine.state.board.tiles.isEmpty())
        assertTrue(engine.state.taskTray.tiles.isEmpty())
    }

    @Test
    fun fixedLevelsHavePlayableSolutions() {
        (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL)
            .forEach { assertSolutionPlays(it) }
    }

    @Test
    fun generatedSolutionsPlayInTheEngine() {
        for (week in SemesterDifficulty.weeks) {
            repeat(10) { seed ->
                assertSolutionPlays(
                    LevelGenerator(seed.toLong()).generate(
                        "test",
                        "Test",
                        week.levels.single()
                    )
                )
            }
        }
    }

    @Test
    fun forcedStackCanBeUnsolvableDespiteTripleCompatibleCounts() {
        val fourTypes = listOf(
            TileType.BOOK,
            TileType.COFFEE,
            TileType.LAPTOP,
            TileType.DEFAULT
        )

        // Seven forced selections fill the tray before any triple is completed.
        val level = stack(fourTypes + fourTypes + fourTypes)

        assertEquals(
            SolvabilityResult.Unsolvable,
            validator.validate(level)
        )

        val engine = DefaultGameEngine(level)

        level.board.tiles
            .take(7)
            .forEach { engine.selectTile(it.id) }

        assertEquals(GameStatus.LOST, engine.state.status)
    }

    @Test
    fun seventhTileCompletesMatchBeforeCapacityIsChecked() {
        val b = TileType.BOOK
        val c = TileType.COFFEE
        val l = TileType.LAPTOP
        val d = TileType.DEFAULT

        assertSolutionPlays(
            stack(
                listOf(
                    b, c, l, d,
                    b, c,
                    b, c,
                    l, l,
                    d, d
                )
            )
        )
    }


    @Test
    fun limitsAreInconclusiveAndDoNotContaminateLaterCalls() {
        val solver = BacktrackingSolver(maxVisitedStates = 1)
        val board = stack(List(3) { TileType.BOOK }).board

        repeat(2) {
            assertEquals(
                SolvabilityResult.SearchLimitReached,
                solver.solve(board)
            )
        }

        assertSolutionPlays(
            stack(List(3) { TileType.BOOK })
        )

        assertEquals(
            SolvabilityResult.SearchLimitReached,
            BacktrackingSolver(maxSearchTiles = 2).solve(board)
        )
    }

    @Test
    fun invalidInputsAreReported() {
        assertTrue(
            validator.validate(Board()) is SolvabilityResult.InvalidBoard
        )

        val level = stack(List(3) { TileType.BOOK })
        val tiles = level.board.tiles

        assertTrue(
            validator.validate(
                Board(tiles + tiles.first())
            ) is SolvabilityResult.InvalidBoard
        )

        assertTrue(
            validator.validate(
                Board(
                    tiles.map {
                        it.copy(position = TilePosition(0, 0))
                    }
                )
            ) is SolvabilityResult.InvalidBoard
        )

        assertTrue(
            validator.validate(
                level.copy(
                    config = level.config.copy(tileCount = 6)
                )
            ) is SolvabilityResult.InvalidBoard
        )

        assertTrue(
            validator.validate(
                level.copy(
                    config = level.config.copy(maxLayer = 0)
                )
            ) is SolvabilityResult.InvalidBoard
        )
    }

    @Test
    fun solverMatchesAcrossTileVarieties() {

        // Independent reference: replay possible moves through the engine itself.
        fun engineCanWin(level: Level): Boolean {
            val failed = mutableSetOf<Set<String>>()

            fun visit(path: List<String>): Boolean {
                val engine = DefaultGameEngine(level)

                path.forEach { engine.selectTile(it) }

                if (engine.state.status == GameStatus.WON) {
                    return engine.state.taskTray.tiles.isEmpty()
                }

                if (engine.state.status == GameStatus.LOST) {
                    return false
                }

                val key = engine.state.board.tiles
                    .map { it.id }
                    .toSet()

                if (key in failed) {
                    return false
                }

                for (tile in engine.state.board.tiles) {
                    if (
                        engine.isTileSelectable(tile.id) &&
                        visit(path + tile.id)
                    ) {
                        return true
                    }
                }

                failed.add(key)
                return false
            }

            return visit(emptyList())
        }

        for (variety in 3..6) {
            val activeTypes = supportedTypes.take(variety)

            assertEquals(
                "Test fixture does not contain $variety tile types",
                variety,
                activeTypes.size
            )

            repeat(10) { seed ->
                val types = activeTypes
                    .flatMap { type -> List(3) { type } }
                    .shuffled(Random(seed))

                val columns = 3

                val level = Level(
                    id = "branch_$variety",
                    name = "Branch $variety",
                    board = Board(
                        types.mapIndexed { index, type ->
                            Tile(
                                id = "t$index",
                                type = type,
                                position = TilePosition(
                                    row = 0,
                                    column = (index % columns) * 2,
                                    layer = index / columns
                                )
                            )
                        }
                    ),
                    config = LevelConfig(
                        layout = LayoutTemplate(1, columns),
                        tileCount = types.size,
                        maxLayer = types.lastIndex / columns,
                        tileVariety = variety
                    )
                )

                val result = validator.validate(level)

                assertEquals(
                    "Variety $variety, seed $seed",
                    engineCanWin(level),
                    result is SolvabilityResult.Solvable
                )

                if (result is SolvabilityResult.Solvable) {
                    assertSolutionPlays(level)
                } else {
                    assertEquals(
                        SolvabilityResult.Unsolvable,
                        result
                    )
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSearchBudget() {
        BacktrackingSolver(maxVisitedStates = 0)
    }
}
