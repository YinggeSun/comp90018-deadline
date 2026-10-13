package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.generator.GeneratedLevelSource
import com.comp90018.deadline.domain.level.model.Level
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRenderingWorkTest {
    private class CountingEngine(val delegate: GameEngine) : GameEngine by delegate {
        var selectionQueries = 0

        override fun isTileSelectable(tileId: String): Boolean {
            selectionQueries++
            return delegate.isTileSelectable(tileId)
        }
    }

    private fun level(): Level = runBlocking { checkNotNull(GeneratedLevelSource().load("level_6")) }

    @Test
    fun hundredStressTicksDoNotRequeryUnchangedBoardTopology() {
        val level = level()
        assertEquals(63, level.board.tiles.size)
        val engine = CountingEngine(DefaultGameEngine(level, week = level.week))
        val model = GameViewModel(level.id, findLevel = { level }, createEngine = { engine })
        val initialQueries = engine.selectionQueries
        repeat(100) { model.onTimeElapsed(100) }
        assertTrue(model.uiState.value.stress > 0)
        assertEquals("Stress changes must not rebuild all 63 tile models", initialQueries, engine.selectionQueries)
    }

    @Test
    fun boardActionsStillPublishActualEngineAvailability() {
        val level = level()
        val engine = CountingEngine(DefaultGameEngine(level, week = level.week))
        val model = GameViewModel(level.id, findLevel = { level }, createEngine = { engine })

        fun verify() {
            val ui = model.uiState.value
            assertEquals(engine.state.board.tiles.map { it.id }.toSet(), ui.boardTiles.map { it.id }.toSet())
            ui.boardTiles.forEach { assertEquals(engine.delegate.isTileSelectable(it.id), it.isSelectable) }
            assertEquals(engine.state.taskTray.tiles.map { it.id }, ui.trayTiles.map { it.id })
            assertEquals(engine.state.status, ui.status)
            assertEquals(engine.canUndo, ui.canUndo)
        }
        repeat(20) {
            model.onEvent(GameUiEvent.TileTapped(model.uiState.value.boardTiles.first { it.isSelectable }.id))
            verify()
            model.undo()
            verify()
            model.onShuffleRequested()
            verify()
            model.onTimeElapsed(100)
            verify()
            model.restart()
            verify()
        }
    }

    @Test
    fun terminalStatusDisablesTilesEvenWhenTheBoardObjectIsUnchanged() {
        val level = level()
        val real = DefaultGameEngine(level)
        var status = GameStatus.RUNNING
        val engine =
            object : GameEngine by real {
                override val state get() = real.state.copy(status = status)

                override fun isTileSelectable(tileId: String) = status == GameStatus.RUNNING && real.isTileSelectable(tileId)
            }
        val model = GameViewModel(level.id, findLevel = { level }, createEngine = { engine })
        assertTrue(model.uiState.value.boardTiles.any { it.isSelectable })
        status = GameStatus.LOST
        model.onEvent(GameUiEvent.TileTapped("missing"))
        assertEquals(GameStatus.LOST, model.uiState.value.status)
        assertFalse(model.uiState.value.boardTiles.any { it.isSelectable })
    }
}
