package com.comp90018.deadline.feature.levelselect

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.data.fake.FakeProgressRepository
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import com.comp90018.deadline.domain.level.model.SemesterLevel
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

    private val level1 = SemesterLevel(1)
    private val level2 = SemesterLevel(2)
    private val level3 = SemesterLevel(3)

    @Test
    fun listsTheSixLevelsInOrder() {
        val levels = LevelSelectViewModel().uiState.value.levels

        assertEquals(SemesterDifficulty.TOTAL_LEVELS, levels.size)
        assertEquals((1..6).map { "level_$it" }, levels.map { it.id })
        assertEquals((1..6).map { "Level $it" }, levels.map { it.name })
    }

    @Test
    fun eachLevelCoversTwoWeeks() {
        val levels = LevelSelectViewModel().uiState.value.levels

        assertEquals((1..6).map { it * 2 - 1 }, levels.map { it.firstWeek })
        assertEquals((1..6).map { it * 2 }, levels.map { it.lastWeek })
    }

    @Test
    fun describesTileAndLayerCountsFromTheConfiguration() {
        val levels = LevelSelectViewModel().uiState.value.levels

        SemesterLevel.ALL.zip(levels).forEach { (expected, item) ->
            val config = SemesterDifficulty.forLevel(expected.number).levels.single()
            assertEquals(config.tileCount, item.tileCount)
            assertEquals(config.maxLayer + 1, item.layerCount)
        }
    }

    @Test
    fun newPlayerHasOnlyLevelOneUnlockedAndNoBestTimes() {
        val levels = LevelSelectViewModel().uiState.value.levels

        assertFalse(levels.first().isLocked)
        assertTrue(levels.drop(1).all { it.isLocked })
        assertTrue(levels.all { it.bestTimeSeconds == null })
    }

    @Test
    fun usesSuppliedProgress() {
        val progress =
            PlayerProgress(
                highestUnlockedWeek = level2.firstWeek,
                personalBests = mapOf(level1.id to PersonalBest(level1.id, 42_900, 0)),
            )
        val levels = LevelSelectViewModel(progress = flowOf(progress)).uiState.value.levels.associateBy { it.id }

        assertFalse(levels.getValue(level2.id).isLocked)
        assertTrue(levels.getValue(level3.id).isLocked)
        assertEquals(42L, levels.getValue(level1.id).bestTimeSeconds)
        assertNull(levels.getValue(level2.id).bestTimeSeconds)
    }

    @Test
    fun winningALevelUnlocksTheNextOne() =
        runBlocking {
            val repository = FakeProgressRepository()
            val viewModel = LevelSelectViewModel(progress = repository.progress)

            // The game records a level's last week, which unlocks the next level's first week.
            repository.recordCompletion(CompletionResult(level1.id, level1.lastWeek, 30_000, 1))
            val levels = viewModel.uiState.value.levels.associateBy { it.id }

            assertFalse(levels.getValue(level2.id).isLocked)
            assertTrue(levels.getValue(level3.id).isLocked)
            assertEquals(30L, levels.getValue(level1.id).bestTimeSeconds)
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
