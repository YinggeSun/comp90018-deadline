package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.engine.CompletionTimer
import com.comp90018.deadline.domain.game.engine.GameEngine
import com.comp90018.deadline.domain.game.model.GameState
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.CompletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCompletionRecordingTest {

    private val recorded = mutableListOf<CompletionResult>()
    private var nowNanos = 0L

    private fun viewModel(levelId: String) = GameViewModel(
        levelId,
        timer = CompletionTimer { nowNanos },
        completionRecorder = CompletionRecorder { recorded += it },
        nowMillis = { 123L }
    )

    private fun GameViewModel.clearBoard(levelId: String) {
        val level = (FixedLevels.ALL_LEVELS + FixedLevels.SAMPLE_LEVEL).single { it.id == levelId }
        level.board.tiles.forEach { onEvent(GameUiEvent.TileTapped(it.id)) }
    }

    @Test
    fun winIsRecordedOnceWithLevelWeekAndTime() {
        val viewModel = viewModel(FixedLevels.LEVEL_2.id)

        nowNanos = 12_345_000_000L
        viewModel.clearBoard(FixedLevels.LEVEL_2.id)
        viewModel.onEvent(GameUiEvent.TileTapped("anything"))

        assertEquals(GameStatus.WON, viewModel.uiState.value.status)
        assertEquals(listOf(CompletionResult(FixedLevels.LEVEL_2.id, 2, 12_345, 123)), recorded)
    }

    @Test
    fun replayingAfterRestartRecordsAgain() {
        val viewModel = viewModel(FixedLevels.LEVEL_1.id)
        nowNanos = 5_000_000_000L
        viewModel.clearBoard(FixedLevels.LEVEL_1.id)

        viewModel.restart()
        nowNanos += 4_000_000_000L
        viewModel.clearBoard(FixedLevels.LEVEL_1.id)

        assertEquals(listOf(5_000L, 4_000L), recorded.map { it.timeMillis })
    }

    @Test
    fun lossIsNotRecorded() {
        // No fixed level can be lost yet (three tile types never fill a 7-slot tray),
        // so drive the ViewModel with an engine whose next selection loses.
        val losingEngine = object : GameEngine {
            override var state = GameState(board = FixedLevels.LEVEL_1.board)
            override val canUndo = false
            override fun selectTile(tileId: String) {
                state = state.copy(status = GameStatus.LOST)
            }
            override fun isTileSelectable(tileId: String) = true
            override fun undo() = Unit
            override fun shuffle() = Unit
            override fun restart() = Unit
        }
        val viewModel = GameViewModel(
            FixedLevels.LEVEL_1.id,
            completionRecorder = CompletionRecorder { recorded += it }
        ) { losingEngine }

        viewModel.onEvent(GameUiEvent.TileTapped("level_1_book_1"))

        assertEquals(GameStatus.LOST, viewModel.uiState.value.status)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun instantWinStillHasPositiveTime() {
        val viewModel = viewModel(FixedLevels.LEVEL_1.id)

        viewModel.clearBoard(FixedLevels.LEVEL_1.id)

        assertEquals(1L, recorded.single().timeMillis)
    }
}
