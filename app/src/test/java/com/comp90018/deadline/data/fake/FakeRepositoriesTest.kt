package com.comp90018.deadline.data.fake

import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.comp90018.deadline.domain.leaderboard.LeaderboardState
import com.comp90018.deadline.domain.leaderboard.SubmitResult
import com.comp90018.deadline.domain.progress.CompletionResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the fakes honour their interface contracts, so tests built on them are meaningful. */
class FakeRepositoriesTest {
    @Test
    fun progressRepositoryAppliesPersonalBestRule() =
        runTest {
            val repository = FakeProgressRepository()

            repository.recordCompletion(CompletionResult("level_1", 1, 50_000, 1))
            val outcome = repository.recordCompletion(CompletionResult("level_1", 1, 70_000, 2))

            assertFalse(outcome.isNewPersonalBest)
            assertEquals(50_000L, repository.progress.first().bestFor("level_1")?.timeMillis)
        }

    @Test
    fun settingsRepositoryUpdatesAtomically() =
        runTest {
            val repository = FakeSettingsRepository()

            repository.update { it.copy(nickname = "Lav", tiltToPeekEnabled = false) }

            val settings = repository.settings.first()
            assertEquals("Lav", settings.nickname)
            assertFalse(settings.tiltToPeekEnabled)
            assertTrue(settings.shakeToShuffleEnabled)
        }

    @Test
    fun authRepositoryFailsWhenOffline() =
        runTest {
            val repository = FakeAuthRepository(userIdOnSignIn = null)

            assertTrue(repository.ensureSignedIn().isFailure)
            repository.userIdOnSignIn = "abc"
            assertEquals("abc", repository.ensureSignedIn().getOrThrow())
        }

    @Test
    fun leaderboardRanksFastestFirstAndKeepsBestPerPlayer() =
        runTest {
            val repository = FakeLeaderboardRepository(userId = "me")
            repository.seed(LeaderboardEntry("other", "Other", "level_1", 40_000, 0))

            assertEquals(SubmitResult.Submitted, repository.submit(CompletionResult("level_1", 1, 50_000, 1), "Me"))
            assertEquals(SubmitResult.NotFaster, repository.submit(CompletionResult("level_1", 1, 90_000, 2), "Me"))

            val state = repository.observe("level_1").first() as LeaderboardState.Ranked
            assertEquals(listOf(40_000L, 50_000L), state.entries.map { it.timeMillis })
        }

    @Test
    fun leaderboardQueuesOfflineSubmissionsInsteadOfFailing() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.offline = true

            assertEquals(SubmitResult.Queued, repository.submit(CompletionResult("level_1", 1, 50_000, 1), "Me"))
            assertTrue(repository.observe("level_1").first() is LeaderboardState.Offline)

            repository.offline = false
            val state = repository.observe("level_1").first() as LeaderboardState.Ranked
            assertEquals(listOf(50_000L), state.entries.map { it.timeMillis })
        }

    @Test
    fun leaderboardFailuresCarryTypedReasons() =
        runTest {
            val repository = FakeLeaderboardRepository()
            repository.failure = LeaderboardFailure.NOT_SIGNED_IN

            assertEquals(
                SubmitResult.Failed(LeaderboardFailure.NOT_SIGNED_IN),
                repository.submit(CompletionResult("level_1", 1, 50_000, 1), "Me"),
            )
            assertEquals(
                LeaderboardState.Error(LeaderboardFailure.NOT_SIGNED_IN),
                repository.observe("level_1").first(),
            )
        }
}
