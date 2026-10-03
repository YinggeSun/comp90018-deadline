package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.LayoutTemplate
import com.comp90018.deadline.domain.level.model.LevelConfig
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LevelGeneratorTest {
    private fun intersects(a: TilePosition, b: TilePosition) =
        abs(a.row.toLong() - b.row) < 2 && abs(a.column.toLong() - b.column) < 2

    @Test
    fun allWeeksHaveValidGeometryAndEngineAvailability() {
        for (week in SemesterDifficulty.weeks) {
            for (config in week.levels) {
                repeat(100) { seed ->
                    val level = LevelGenerator(seed.toLong()).generate("test", "Test", config)
                    val tiles = level.board.tiles
                    assertEquals(config.tileCount, tiles.size)
                    assertEquals(tiles.size, tiles.map { it.id }.toSet().size)
                    assertEquals(tiles.size, tiles.map { it.position }.toSet().size)
                    assertFalse(tiles.any { it.type == TileType.DEFAULT })
                    assertTrue(tiles.groupingBy { it.type }.eachCount().values.all { it % 3 == 0 })
                    assertEquals((0..config.maxLayer).toSet(), tiles.map { it.position.layer }.toSet())
                    for (tile in tiles) {
                        val p = tile.position
                        assertTrue(p.row in 0..(config.layout.rows * 2 - 1))
                        assertTrue(p.column in 0..(config.layout.columns * 2 - 1))
                        assertFalse(tiles.any {
                            it.id != tile.id && it.position.layer == p.layer && intersects(p, it.position)
                        })
                        if (p.layer > 0) assertTrue(tiles.any {
                            it.position.layer == p.layer - 1 && intersects(p, it.position)
                        })
                    }
                    val engine = DefaultGameEngine(level)
                    for (tile in tiles) {
                        val blocked = tiles.any {
                            it.position.layer > tile.position.layer && intersects(tile.position, it.position)
                        }
                        assertEquals(!blocked, engine.isTileSelectable(tile.id))
                    }
                    val top = tiles.first { it.position.layer == config.maxLayer }
                    engine.selectTile(top.id)
                    assertEquals(tiles.size - 1, engine.state.board.tiles.size)
                    engine.restart()
                    assertEquals(level.board, engine.state.board)
                }
            }
        }
    }

    @Test
    fun sameSeedReproducesWholeBoardOnRepeatedCalls() {
        val config = SemesterDifficulty.forWeek(12).levels.single()
        val generator = LevelGenerator(42)
        assertEquals(generator.generateBoard(config), generator.generateBoard(config))
        assertEquals(generator.generateBoard(config), LevelGenerator(42).generateBoard(config))
        assertNotEquals(generator.generateBoard(config), LevelGenerator(43).generateBoard(config))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInsufficientCapacity() {
        LevelGenerator(1).generateBoard(LevelConfig(LayoutTemplate(1, 1), 3, 1))
    }

    @Test
    fun fullCapacityAndSingleCellStacksWork() {
        for (config in listOf(
            LevelConfig(LayoutTemplate(2, 3), 18, 2),
            LevelConfig(LayoutTemplate(1, 1), 3, 2),
            LevelConfig(LayoutTemplate(1, 1), 3, Int.MAX_VALUE)
        )) {
            val board = LevelGenerator(42).generateBoard(config)
            assertEquals(config.tileCount, board.tiles.size)
            assertEquals(config.tileCount, board.tiles.map { it.position }.toSet().size)
        }
    }
}
