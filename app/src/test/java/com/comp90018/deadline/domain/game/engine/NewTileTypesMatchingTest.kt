package com.comp90018.deadline.domain.game.engine

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.LayoutTemplate
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.LevelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tile types added for #100 match like any other tile. */
class NewTileTypesMatchingTest {
    private val newTypes = listOf(TileType.MUSIC, TileType.EXAM, TileType.PRESENTATION, TileType.LECTURE_SLIDE)

    /** One triple of each type in [types], side by side on one layer so every tile is selectable. */
    private fun level(types: List<TileType>): Level {
        val tiles =
            types.flatMap { type -> List(3) { type } }.mapIndexed { index, type ->
                Tile(id = "tile_$index", type = type, position = TilePosition(row = 0, column = index * 2, layer = 0))
            }
        return Level(
            id = "new_types",
            name = "New types",
            board = Board(tiles),
            config =
                LevelConfig(
                    layout = LayoutTemplate(rows = 2, columns = tiles.size * 2),
                    tileCount = tiles.size,
                    maxLayer = 0,
                    tileVariety = types.size,
                ),
        )
    }

    @Test
    fun eachNewTypeFormsANormalTriple() {
        for (type in newTypes) {
            val engine = DefaultGameEngine(level(listOf(type)))

            engine.state.board.tiles.forEach { engine.selectTile(it.id) }

            assertTrue("$type triple should clear the tray", engine.state.taskTray.tiles.isEmpty())
            assertEquals("$type level should be won", GameStatus.WON, engine.state.status)
        }
    }

    @Test
    fun newTypesOnlyMatchTheirOwnType() {
        val engine = DefaultGameEngine(level(newTypes))
        val tiles = engine.state.board.tiles

        // Two each of three new types: no triple forms, so all six stay in the tray.
        tiles.groupBy { it.type }.values.flatMap { it.take(2) }.take(6).forEach { engine.selectTile(it.id) }

        assertEquals(6, engine.state.taskTray.tiles.size)
        assertEquals(GameStatus.RUNNING, engine.state.status)
    }
}
