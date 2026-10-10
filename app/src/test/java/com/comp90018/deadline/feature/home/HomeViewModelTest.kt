package com.comp90018.deadline.feature.home

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.data.fake.FakeProgressRepository
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun nothingToContinueForNewPlayer() {
        assertNull(HomeViewModel().uiState.value.continueLevelId)
    }

    @Test
    fun continuesAtFirstUnlockedUnclearedLevel() =
        runBlocking {
            val repository = FakeProgressRepository()
            val viewModel = HomeViewModel(progress = repository.progress)

            // The game records a level's last week, which unlocks the next level.
            repository.recordCompletion(CompletionResult(SemesterLevel(1).id, SemesterLevel(1).lastWeek, 30_000, 1))

            assertEquals(SemesterLevel(2).id, viewModel.uiState.value.continueLevelId)
        }

    @Test
    fun nothingToContinueWhenEveryUnlockedLevelIsCleared() {
        val progress =
            PlayerProgress(
                completedLevelIds = setOf(SemesterLevel(1).id),
                highestUnlockedWeek = SemesterLevel(1).lastWeek,
            )

        assertNull(HomeViewModel(progress = flowOf(progress)).uiState.value.continueLevelId)
    }
}
