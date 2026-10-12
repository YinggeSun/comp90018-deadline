package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.local.DataStoreTestRule
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Issue #43: repository contracts against the production DataStore factory and a real file. */
class PersistenceRepositoryTest {
    @get:Rule
    val store = DataStoreTestRule()

    private fun progress() = ProgressRepositoryImpl(ProgressDataSource(store.dataStore))

    private fun settings() = SettingsRepositoryImpl(SettingsDataSource(store.dataStore))

    @Test
    fun freshRepositoriesExposeExplicitDefaults() =
        runBlocking {
            val progress = progress().progress.first()
            val settings = settings().settings.first()
            assertEquals(emptySet<String>(), progress.completedLevelIds)
            assertEquals(1, progress.highestUnlockedWeek)
            assertEquals(emptyMap<String, PersonalBest>(), progress.personalBests)
            assertEquals(0L, progress.lastModifiedMillis)
            assertNull(settings.nickname)
            assertTrue(settings.backgroundMusicEnabled)
            assertTrue(settings.soundEffectsEnabled)
            assertTrue(settings.hapticsEnabled)
            assertTrue(settings.shakeToShuffleEnabled)
            assertTrue(settings.tiltToPeekEnabled)
        }

    @Test
    fun settingsAndProgressShareOneFileWithoutOverwritingEachOther() =
        runBlocking {
            val progress = progress()
            val settings = settings()
            val expectedSettings =
                PlayerSettings("QA", false, false, false, false, false)
            settings.update { expectedSettings }
            progress.recordCompletion(CompletionResult("week:12:final", 12, 42_000, 100))
            settings.update { it.copy(nickname = "QA updated", soundEffectsEnabled = true) }
            progress.recordCompletion(CompletionResult("week:12:final", 12, 35_000, 200))

            store.reopen()
            assertEquals(expectedSettings.copy(nickname = "QA updated", soundEffectsEnabled = true), settings().settings.first())
            assertEquals(
                PlayerProgress(
                    setOf("week:12:final"),
                    12,
                    mapOf("week:12:final" to PersonalBest("week:12:final", 35_000, 200)),
                    200,
                ),
                progress().progress.first(),
            )
        }

    @Test
    fun bestOutcomesAndUnlocksSurviveRepositoryRestart() =
        runBlocking {
            val repository = progress()
            val first = repository.recordCompletion(CompletionResult("level_1", 1, 40_000, 10))
            assertTrue(first.isNewPersonalBest)
            assertNull(first.previousBest)
            assertEquals(2, first.newlyUnlockedWeek)
            val equal = repository.recordCompletion(CompletionResult("level_1", 1, 40_000, 20))
            assertFalse(equal.isNewPersonalBest)
            assertNull(equal.newlyUnlockedWeek)
            val faster = repository.recordCompletion(CompletionResult("level_1", 1, 30_000, 30))
            assertEquals(PersonalBest("level_1", 40_000, 10), faster.previousBest)
            assertTrue(faster.isNewPersonalBest)
            repository.recordCompletion(CompletionResult("level_1", 1, 50_000, 25))

            store.reopen()
            val restored = progress().progress.first()
            assertEquals(PersonalBest("level_1", 30_000, 30), restored.bestFor("level_1"))
            assertEquals(30L, restored.lastModifiedMillis)
            assertEquals(2, restored.highestUnlockedWeek)
        }

    @Test
    fun mergingEqualTimesKeepsEarlierRecordAndPreservesSettings() =
        runBlocking {
            settings().update { it.copy(nickname = "QA", tiltToPeekEnabled = false) }
            progress().recordCompletion(CompletionResult("level_1", 1, 30_000, 100))
            val remote =
                PlayerProgress(
                    setOf("level_1", "level_2"),
                    5,
                    mapOf("level_1" to PersonalBest("level_1", 30_000, 50)),
                    200,
                )
            val merged = progress().mergeIn(remote)
            assertEquals(PersonalBest("level_1", 30_000, 50), merged.bestFor("level_1"))
            assertEquals(merged, progress().mergeIn(remote))
            assertEquals(merged, progress().mergeIn(PlayerProgress()))

            store.reopen()
            assertEquals(merged, progress().progress.first())
            assertEquals(setOf("level_1", "level_2"), merged.completedLevelIds)
            assertEquals(5, merged.highestUnlockedWeek)
            assertEquals(200L, merged.lastModifiedMillis)
            assertEquals("QA", settings().settings.first().nickname)
            assertFalse(settings().settings.first().tiltToPeekEnabled)
        }

    @Test
    fun concurrentCompletionsMergesAndSettingsUpdatesLoseNoRecords() =
        runBlocking {
            val progress = progress()
            val settings = settings()
            val jobs =
                (1..20).flatMap { i ->
                    listOf(
                        async(Dispatchers.Default) {
                            progress.recordCompletion(CompletionResult("local_$i", 1, 1_000L * i, i.toLong()))
                        },
                        async(Dispatchers.Default) {
                            progress.mergeIn(
                                PlayerProgress(
                                    setOf("cloud_$i"),
                                    7,
                                    mapOf("cloud_$i" to PersonalBest("cloud_$i", 2_000L * i, i.toLong())),
                                    100L + i,
                                ),
                            )
                        },
                        async(Dispatchers.Default) { settings.update { it.copy(hapticsEnabled = !it.hapticsEnabled) } },
                    )
                }
            jobs.awaitAll()

            store.reopen()
            val restored = progress().progress.first()
            val expectedIds = (1..20).flatMap { listOf("local_$it", "cloud_$it") }.toSet()
            assertEquals(expectedIds, restored.completedLevelIds)
            assertEquals(expectedIds, restored.personalBests.keys)
            assertEquals(7, restored.highestUnlockedWeek)
            assertEquals(120L, restored.lastModifiedMillis)
            (1..20).forEach { i ->
                assertEquals(PersonalBest("local_$i", 1_000L * i, i.toLong()), restored.bestFor("local_$i"))
                assertEquals(PersonalBest("cloud_$i", 2_000L * i, i.toLong()), restored.bestFor("cloud_$i"))
            }
            assertEquals(PlayerSettings(), settings().settings.first())
        }

    @Test
    fun competingRecordsForSameLevelAlwaysPersistFastestTime() =
        runBlocking {
            val repository = progress()
            (1..20).map { i ->
                async(Dispatchers.Default) {
                    repository.recordCompletion(CompletionResult("same", 1, 1_000L * i, i.toLong()))
                }
            }.awaitAll()

            store.reopen()
            assertEquals(PersonalBest("same", 1_000, 1), progress().progress.first().bestFor("same"))
            assertEquals(20L, progress().progress.first().lastModifiedMillis)
        }

    @Test
    fun throwingSettingsTransformDoesNotCommitPartialChanges() =
        runBlocking {
            val expected = PlayerSettings(nickname = "QA", hapticsEnabled = false)
            settings().update { expected }
            progress().recordCompletion(CompletionResult("level_1", 1, 10_000, 10))
            val expectedProgress = progress().progress.first()
            var failed = false
            try {
                settings().update { throw IllegalStateException("Rejected update") }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)

            store.reopen()
            assertEquals(expected, settings().settings.first())
            assertEquals(expectedProgress, progress().progress.first())
        }

    @Test
    fun corruptSharedFileResetsBothRepositoriesAndRemainsWritable() =
        runBlocking {
            settings().update { it.copy(nickname = "Before corruption") }
            progress().recordCompletion(CompletionResult("old", 1, 10_000, 1))
            store.corrupt(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07))
            store.reopen()
            assertEquals(PlayerSettings(), settings().settings.first())
            assertEquals(PlayerProgress(), progress().progress.first())

            settings().update { it.copy(nickname = "Recovered", shakeToShuffleEnabled = false) }
            progress().recordCompletion(CompletionResult("new", 3, 20_000, 2))
            store.reopen()
            assertEquals("Recovered", settings().settings.first().nickname)
            assertFalse(settings().settings.first().shakeToShuffleEnabled)
            assertEquals(setOf("new"), progress().progress.first().completedLevelIds)
            assertEquals(PersonalBest("new", 20_000, 2), progress().progress.first().bestFor("new"))
            assertEquals(4, progress().progress.first().highestUnlockedWeek)
        }
}
