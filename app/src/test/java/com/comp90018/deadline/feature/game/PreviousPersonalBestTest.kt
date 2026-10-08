package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.game.engine.CompletionTimer
import com.comp90018.deadline.domain.game.model.GameStatus
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.progress.CompletionRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Result screen decides "new Personal Best" from the best saved before this run, so the
 * Game screen must publish it together with the win, before the new time is saved.
 */
class PreviousPersonalBestTest {
    private val level = FixedLevels.LEVEL_1

    /** Saved bests, updated synchronously by the recorder, so a late read would see the new time. */
    private val saved = mutableMapOf<String, Long>()
    private var nowNanos = 0L

    private fun viewModel() =
        GameViewModel(
            level.id,
            timer = CompletionTimer { nowNanos },
            completionRecorder = CompletionRecorder { saved[it.levelId] = minOf(saved[it.levelId] ?: Long.MAX_VALUE, it.timeMillis) },
            personalBestMillis = { saved[it] },
        )

    private fun GameViewModel.win(seconds: Long) {
        nowNanos += seconds * 1_000_000_000L
        level.board.tiles.forEach { onEvent(GameUiEvent.TileTapped(it.id)) }
    }

    @Test
    fun winPublishesTheBestFromBeforeThisRun() {
        saved[level.id] = 60_000L
        val viewModel = viewModel()

        viewModel.win(seconds = 12)

        assertEquals(GameStatus.WON, viewModel.uiState.value.status)
        assertEquals(60_000L, viewModel.uiState.value.previousBestMillis)
        assertEquals(12_000L, saved[level.id])
    }

    @Test
    fun firstClearHasNoPreviousBest() {
        viewModel().also { it.win(seconds = 12) }.let {
            assertEquals(GameStatus.WON, it.uiState.value.status)
            assertNull(it.uiState.value.previousBestMillis)
        }
    }

    @Test
    fun notPublishedWhileRunning() {
        saved[level.id] = 60_000L
        val viewModel = viewModel()

        viewModel.onEvent(GameUiEvent.TileTapped(level.board.tiles.first().id))

        assertNull(viewModel.uiState.value.previousBestMillis)
    }

    @Test
    fun replayAfterRestartComparesAgainstTheFirstRun() {
        val viewModel = viewModel()
        viewModel.win(seconds = 30)

        viewModel.restart()
        assertNull(viewModel.uiState.value.previousBestMillis)
        viewModel.win(seconds = 20)

        assertEquals(30_000L, viewModel.uiState.value.previousBestMillis)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun noEmittedStateShowsTheWinWithoutThePreviousBest() =
        runTest {
            saved[level.id] = 60_000L
            val viewModel = viewModel()
            val states = mutableListOf<GameUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }

            viewModel.win(seconds = 12)

            val wins = states.filter { it.status == GameStatus.WON }
            assertTrue(wins.isNotEmpty())
            assertTrue(wins.all { it.previousBestMillis == 60_000L })
        }
}
