package com.comp90018.deadline.feature.levelselect

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.data.fake.FakeProgressRepository
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LevelSelectViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun listsFixedLevelsInOrder() {
        val levels = LevelSelectViewModel().uiState.value.levels

        assertEquals(FixedLevels.ALL_LEVELS.map { it.id }, levels.map { it.id })
        assertEquals(FixedLevels.ALL_LEVELS.map { it.name }, levels.map { it.name })
    }

    @Test
    fun describesTileAndLayerCounts() {
        val level3 = LevelSelectViewModel().uiState.value.levels.single { it.id == FixedLevels.LEVEL_3.id }

        assertEquals(9, level3.tileCount)
        assertEquals(2, level3.layerCount)
    }

    @Test
    fun newPlayerHasOnlyWeekOneUnlockedAndNoBestTimes() {
        val levels = LevelSelectViewModel().uiState.value.levels.associateBy { it.id }

        assertFalse(levels.getValue(FixedLevels.LEVEL_1.id).isLocked)
        assertTrue(levels.getValue(FixedLevels.LEVEL_2.id).isLocked)
        assertTrue(levels.getValue(FixedLevels.LEVEL_3.id).isLocked)
        assertTrue(levels.values.all { it.bestTimeSeconds == null })
    }

    @Test
    fun usesSuppliedProgress() {
        val progress =
            PlayerProgress(
                highestUnlockedWeek = 2,
                personalBests = mapOf(FixedLevels.LEVEL_1.id to PersonalBest(FixedLevels.LEVEL_1.id, 42_900, 0)),
            )
        val levels = LevelSelectViewModel(progress = flowOf(progress)).uiState.value.levels.associateBy { it.id }

        assertFalse(levels.getValue(FixedLevels.LEVEL_2.id).isLocked)
        assertTrue(levels.getValue(FixedLevels.LEVEL_3.id).isLocked)
        assertEquals(42L, levels.getValue(FixedLevels.LEVEL_1.id).bestTimeSeconds)
        assertNull(levels.getValue(FixedLevels.LEVEL_2.id).bestTimeSeconds)
    }

    @Test
    fun updatesWhenALevelIsCompleted() =
        runBlocking {
            val repository = FakeProgressRepository()
            val viewModel = LevelSelectViewModel(progress = repository.progress)

            repository.recordCompletion(CompletionResult(FixedLevels.LEVEL_1.id, 1, 30_000, 1))
            val levels = viewModel.uiState.value.levels.associateBy { it.id }

            assertFalse(levels.getValue(FixedLevels.LEVEL_2.id).isLocked)
            assertEquals(30L, levels.getValue(FixedLevels.LEVEL_1.id).bestTimeSeconds)
        }

    @Test
    fun loadingUntilStoredProgressArrives() {
        val state = LevelSelectViewModel(progress = emptyFlow()).uiState.value

        assertTrue(state.isLoading)
        assertTrue(state.levels.isEmpty())
    }

    @Test
    fun notLoadingOnceProgressArrives() {
        assertFalse(LevelSelectViewModel().uiState.value.isLoading)
    }
}
