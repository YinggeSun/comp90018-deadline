package com.comp90018.deadline.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class ProgressDataSourceTest {
    @get:Rule
    val store = DataStoreTestRule()

    @Test
    fun emptyStoreReadsAsFreshProgress() =
        runBlocking {
            assertEquals(PlayerProgress(), ProgressDataSource(store.dataStore).progress.first())
        }

    @Test
    fun progressSurvivesRestart() =
        runBlocking {
            ProgressDataSource(store.dataStore).recordCompletion(CompletionResult("level_1", 1, 42_000, 7))

            val restored = ProgressDataSource(store.reopen()).progress.first()

            assertEquals(setOf("level_1"), restored.completedLevelIds)
            assertEquals(2, restored.highestUnlockedWeek)
            assertEquals(PersonalBest("level_1", 42_000, 7), restored.bestFor("level_1"))
            assertEquals(7L, restored.lastModifiedMillis)
        }

    @Test
    fun slowerResultDoesNotOverwriteStoredBest() =
        runBlocking {
            val source = ProgressDataSource(store.dataStore)
            source.recordCompletion(CompletionResult("level_1", 1, 42_000, 1))

            val outcome = source.recordCompletion(CompletionResult("level_1", 1, 50_000, 2))

            assertFalse(outcome.isNewPersonalBest)
            assertEquals(42_000L, source.progress.first().bestFor("level_1")?.timeMillis)
        }

    @Test
    fun concurrentCompletionsAreNotLost() =
        runBlocking {
            val source = ProgressDataSource(store.dataStore)

            (1..10).map { i ->
                async { source.recordCompletion(CompletionResult("level_$i", 1, 1_000L * i, i.toLong())) }
            }.awaitAll()

            assertEquals(10, source.progress.first().personalBests.size)
        }

    @Test
    fun levelIdsContainingSeparatorRoundTrip() =
        runBlocking {
            val source = ProgressDataSource(store.dataStore)
            source.recordCompletion(CompletionResult("week:3:a", 3, 9_000, 1))

            assertEquals(9_000L, ProgressDataSource(store.reopen()).progress.first().bestFor("week:3:a")?.timeMillis)
        }

    @Test
    fun malformedStoredValuesFallBackSafely() =
        runBlocking {
            store.dataStore.edit {
                it[intPreferencesKey("progress_highest_unlocked_week")] = 99
                it[stringSetPreferencesKey("progress_personal_bests")] =
                    setOf("garbage", "-5:0:level_1", "abc:0:level_2", "3000:1:level_3", "2000:2:level_3")
            }

            val progress = ProgressDataSource(store.dataStore).progress.first()

            assertEquals(12, progress.highestUnlockedWeek)
            assertEquals(setOf("level_3"), progress.personalBests.keys)
            assertEquals(2_000L, progress.bestFor("level_3")?.timeMillis)
        }

    @Test
    fun corruptFileIsReplacedAndProgressCanBeSavedAgain() =
        runBlocking {
            // Field 1 claims ~2 GB of data that is not there: a truncated protobuf.
            store.corrupt(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07))
            val source = ProgressDataSource(store.reopen())

            assertEquals(PlayerProgress(), source.progress.first())
            source.recordCompletion(CompletionResult("level_1", 1, 42_000, 1))

            assertEquals(42_000L, ProgressDataSource(store.reopen()).progress.first().bestFor("level_1")?.timeMillis)
        }
}
