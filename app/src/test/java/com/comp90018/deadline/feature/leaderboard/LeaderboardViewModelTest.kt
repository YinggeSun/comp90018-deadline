package com.comp90018.deadline.feature.leaderboard

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.data.fake.FakeLeaderboardRepository
import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.repository.LeaderboardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LeaderboardViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val level1 = SemesterLevel(1).id
    private val level2 = SemesterLevel(2).id

    private fun entry(
        user: String,
        millis: Long,
        levelId: String = level1,
    ) = LeaderboardEntry(user, user.uppercase(), levelId, millis, 0)

    /** Keeps the live ranking subscribed, as the screen does while it is shown. */
    private fun TestScope.keepCollecting(viewModel: LeaderboardViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun FakeLeaderboardRepository.seed(vararg entries: LeaderboardEntry) = entries.forEach(::seed)

    @Test
    fun ranksFastestFirstAndEqualTimesShareARank() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.seed(entry("c", 50_000), entry("a", 30_000), entry("b", 40_000), entry("d", 40_000))
            val viewModel = LeaderboardViewModel(repository)
            keepCollecting(viewModel)

            val rows = (viewModel.uiState.value.content as LeaderboardContent.Ranked).rows
            assertEquals(listOf("A", "B", "D", "C"), rows.map { it.nickname })
            assertEquals(listOf(1, 2, 2, 4), rows.map { it.rank })
        }

    @Test
    fun marksOnlyTheCurrentPlayersRow() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.seed(entry("other", 30_000), entry("me", 40_000))
            val viewModel = LeaderboardViewModel(repository, currentUserId = flowOf("me"))
            keepCollecting(viewModel)

            val rows = (viewModel.uiState.value.content as LeaderboardContent.Ranked).rows
            assertEquals(listOf(false, true), rows.map { it.isCurrentPlayer })
        }

    @Test
    fun noRowIsMarkedWhileSignedOut() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.seed(entry("me", 40_000))
            val viewModel = LeaderboardViewModel(repository, currentUserId = emptyFlow())
            keepCollecting(viewModel)

            val rows = (viewModel.uiState.value.content as LeaderboardContent.Ranked).rows
            assertEquals(listOf(false), rows.map { it.isCurrentPlayer })
        }

    @Test
    fun switchingLevelShowsThatLevelsRanking() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.seed(entry("a", 30_000, level1), entry("b", 60_000, level2))
            val viewModel = LeaderboardViewModel(repository)
            keepCollecting(viewModel)

            viewModel.selectLevel(level2)

            val state = viewModel.uiState.value
            assertEquals(level2, state.selectedLevelId)
            assertEquals(listOf("B"), (state.content as LeaderboardContent.Ranked).rows.map { it.nickname })
        }

    @Test
    fun unknownLevelIsIgnored() =
        runTest {
            val viewModel = LeaderboardViewModel(FakeLeaderboardRepository())
            keepCollecting(viewModel)

            viewModel.selectLevel("level_99")

            assertEquals(level1, viewModel.uiState.value.selectedLevelId)
        }

    @Test
    fun offersEverySemesterLevel() {
        val viewModel = LeaderboardViewModel(FakeLeaderboardRepository())

        assertEquals(SemesterLevel.ALL.map { it.id }, viewModel.uiState.value.levels.map { it.id })
        assertEquals(SemesterLevel.ALL.map { it.name }, viewModel.uiState.value.levels.map { it.name })
    }

    @Test
    fun updatesLiveWhenANewTimeArrives() =
        runTest {
            val repository = FakeLeaderboardRepository(userId = "me")
            repository.seed(entry("other", 30_000))
            val viewModel = LeaderboardViewModel(repository, currentUserId = flowOf("me"))
            keepCollecting(viewModel)

            repository.submit(CompletionResult(level1, week = 2, timeMillis = 20_000, completedAtMillis = 1), "Me")

            val rows = (viewModel.uiState.value.content as LeaderboardContent.Ranked).rows
            assertEquals(listOf("Me", "OTHER"), rows.map { it.nickname })
            assertTrue(rows.first().isCurrentPlayer)
        }

    @Test
    fun offlineKeepsTheCachedRanking() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.seed(entry("a", 30_000))
            repository.offline = true
            val viewModel = LeaderboardViewModel(repository)
            keepCollecting(viewModel)

            val content = viewModel.uiState.value.content as LeaderboardContent.Offline
            assertEquals(listOf("A"), content.rows.map { it.nickname })
        }

    @Test
    fun failureShowsItsReasonAndRetryLoadsAgain() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.seed(entry("a", 30_000))
            repository.failure = LeaderboardFailure.UNKNOWN
            val viewModel = LeaderboardViewModel(repository)
            keepCollecting(viewModel)
            assertEquals(LeaderboardContent.Error(LeaderboardFailure.UNKNOWN), viewModel.uiState.value.content)

            repository.failure = null
            viewModel.retry()

            assertTrue(viewModel.uiState.value.content is LeaderboardContent.Ranked)
        }

    @Test
    fun showsLoadingRatherThanTheOldLevelWhileTheNewOneLoads() =
        runTest {
            val rankings = mutableMapOf<String, MutableStateFlow<LeaderboardState>>()
            val repository =
                object : LeaderboardRepository by FakeLeaderboardRepository() {
                    override fun observe(
                        levelId: String,
                        limit: Int,
                    ): Flow<LeaderboardState> = rankings.getOrPut(levelId) { MutableStateFlow(LeaderboardState.Loading) }
                }
            val viewModel = LeaderboardViewModel(repository)
            keepCollecting(viewModel)
            rankings.getValue(level1).value = LeaderboardState.Ranked(listOf(entry("a", 30_000)))

            viewModel.selectLevel(level2)

            assertEquals(LeaderboardContent.Loading, viewModel.uiState.value.content)
        }

    @Test
    fun opensOnTheFurthestLevelReached() {
        assertEquals(level1, LeaderboardViewModel.initialLevelFor(PlayerProgress()))
        assertEquals(
            SemesterLevel(3).id,
            LeaderboardViewModel.initialLevelFor(PlayerProgress(highestUnlockedWeek = SemesterLevel(3).firstWeek)),
        )
    }
}
