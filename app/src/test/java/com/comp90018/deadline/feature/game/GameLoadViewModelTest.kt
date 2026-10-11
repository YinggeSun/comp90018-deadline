package com.comp90018.deadline.feature.game

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.domain.level.generator.LevelSource
import com.comp90018.deadline.domain.level.model.FixedLevels
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GameLoadViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val level = FixedLevels.LEVEL_1

    @Test
    fun loadingUntilTheBoardIsReady() {
        val board = CompletableDeferred<Unit>()
        val viewModel =
            GameLoadViewModel(
                level.id,
                LevelSource {
                    board.await()
                    level
                },
            )

        assertEquals(GameLoadState.Loading, viewModel.state.value)

        board.complete(Unit)
        assertEquals(GameLoadState.Ready(level), viewModel.state.value)
    }

    @Test
    fun unknownLevelFails() {
        val viewModel = GameLoadViewModel("missing", LevelSource { null })

        assertEquals(GameLoadState.Failed, viewModel.state.value)
    }

    @Test
    fun generationErrorFailsInsteadOfCrashing() {
        val viewModel = GameLoadViewModel(level.id, LevelSource { throw IllegalStateException("generator broke") })

        assertEquals(GameLoadState.Failed, viewModel.state.value)
    }

    @Test
    fun retryAfterFailureLoadsAgain() {
        var calls = 0
        val viewModel =
            GameLoadViewModel(
                level.id,
                LevelSource {
                    calls++
                    if (calls == 1) null else level
                },
            )
        assertEquals(GameLoadState.Failed, viewModel.state.value)

        viewModel.retry()

        assertEquals(GameLoadState.Ready(level), viewModel.state.value)
        assertEquals(2, calls)
    }
}
