package com.comp90018.deadline.core.audio

import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameAudioManagerTest {
    private class FakePlayer : AudioPlayer {
        override var isMusicPlaying = false
        override var onMusicError: (() -> Unit)? = null
        var startAttempts = 0
        var failStarts = false
        var musicReleases = 0
        var effectsReleases = 0
        val activeEffects = mutableListOf<GameAudioEvent>()
        var prepares = 0

        override fun prepare() {
            prepares++
        }

        override fun startMusic(): Boolean {
            startAttempts++
            isMusicPlaying = !failStarts
            return isMusicPlaying
        }

        override fun pauseMusic() {
            isMusicPlaying = false
        }

        override fun releaseMusic() {
            musicReleases++
            isMusicPlaying = false
        }

        override fun play(event: GameAudioEvent) {
            activeEffects.add(event)
        }

        override fun stopEffects() {
            activeEffects.clear()
        }

        override fun releaseEffects() {
            effectsReleases++
            stopEffects()
        }

        override fun release() {
            releaseMusic()
            releaseEffects()
        }

        fun failMusic() {
            isMusicPlaying = false
            onMusicError?.invoke()
        }
    }

    private val player = FakePlayer()
    private val session = Any()

    private fun TestScope.manager(): GameAudioManager =
        GameAudioManager(player, backgroundScope).also {
            it.setAppForeground(true)
            it.updateSettings(PlayerSettings())
            it.setForeground(session, true)
        }

    @Test
    fun waitsForStoredSettingsAndDisabledMusicNeverStarts() =
        runTest {
            val manager = GameAudioManager(player, backgroundScope)
            manager.setAppForeground(true)
            manager.setForeground(session, true)
            assertEquals(0, player.startAttempts)
            manager.updateSettings(PlayerSettings(backgroundMusicEnabled = false))
            assertEquals(0, player.startAttempts)
            manager.updateSettings(PlayerSettings())
            assertTrue(player.isMusicPlaying)
            manager.updateSettings(PlayerSettings(backgroundMusicEnabled = false))
            assertFalse(player.isMusicPlaying)
            manager.updateSettings(PlayerSettings())
            assertEquals(2, player.startAttempts)
        }

    @Test
    fun finalMatchSurvivesResultNavigationAndViewModelCleanup() =
        runTest {
            val manager = manager()
            manager.play(session, GameAudioEvent.MATCH)
            manager.setForeground(session, true, gameplayActive = false)
            assertFalse(player.isMusicPlaying)
            manager.setForeground(session, false, gameplayActive = false)
            manager.release(session)
            manager.release(session)
            assertEquals(listOf(GameAudioEvent.MATCH), player.activeEffects)
            assertEquals(0, player.effectsReleases)
            manager.setAppForeground(false)
            assertTrue(player.activeEffects.isEmpty())
            assertEquals(1, player.effectsReleases)
            manager.setAppForeground(false)
            assertEquals(1, player.effectsReleases)
        }

    @Test
    fun disablingEffectsStopsCompletionSoundEvenAfterSessionReleased() =
        runTest {
            val manager = manager()
            manager.play(session, GameAudioEvent.MATCH)
            manager.setForeground(session, false, gameplayActive = false)
            manager.release(session)
            manager.updateSettings(PlayerSettings(soundEffectsEnabled = false))
            assertTrue(player.activeEffects.isEmpty())
            manager.setForeground(session, true)
            GameAudioEvent.entries.forEach { manager.play(session, it) }
            assertTrue(player.activeEffects.isEmpty())
        }

    @Test
    fun backgroundReleasesResourcesAndCannotReplayEffectsOnResume() =
        runTest {
            val manager = manager()
            manager.play(session, GameAudioEvent.TILE_CLICK)
            manager.setAppForeground(false)
            manager.play(session, GameAudioEvent.MATCH)
            manager.updateSettings(PlayerSettings())
            assertFalse(player.isMusicPlaying)
            assertTrue(player.activeEffects.isEmpty())
            assertEquals(1, player.effectsReleases)
            manager.setAppForeground(true)
            assertTrue(player.isMusicPlaying)
            assertTrue(player.activeEffects.isEmpty())
        }

    @Test
    fun rotationPausesAndResumesWithoutReleasingSharedResources() =
        runTest {
            val manager = manager()
            val releases = player.musicReleases
            repeat(10) { manager.setForeground(session, true) }
            assertEquals(1, player.startAttempts)
            manager.setForeground(session, false)
            manager.setAppForeground(true) // replacement Activity; old Activity's config stop is ignored
            manager.setForeground(session, true)
            assertEquals(2, player.startAttempts)
            assertEquals(releases, player.musicReleases)
            assertEquals(0, player.effectsReleases)
        }

    @Test
    fun leavingRunningGameStopsEffectsAndSettingsCannotRestartMusic() =
        runTest {
            val manager = manager()
            manager.play(session, GameAudioEvent.MATCH)
            manager.setForeground(session, false)
            manager.updateSettings(PlayerSettings())
            assertTrue(player.activeEffects.isEmpty())
            assertFalse(player.isMusicPlaying)
        }

    @Test
    fun staleSessionCannotReleaseOrPlayOverNewSession() =
        runTest {
            val manager = manager()
            manager.play(session, GameAudioEvent.MATCH)
            val next = Any()
            manager.setForeground(next, true)
            val releases = player.musicReleases
            manager.setForeground(session, false, gameplayActive = false)
            manager.release(session)
            manager.play(session, GameAudioEvent.MATCH)
            assertTrue(player.isMusicPlaying)
            assertEquals(releases, player.musicReleases)
            assertTrue(player.activeEffects.isEmpty())
            manager.play(next, GameAudioEvent.TILE_CLICK)
            assertEquals(listOf(GameAudioEvent.TILE_CLICK), player.activeEffects)
        }

    @Test
    fun failedStartRetriesWithDelayAndSucceedsWithoutDuplicateStarts() =
        runTest {
            player.failStarts = true
            val manager = manager()
            assertFalse(player.isMusicPlaying)
            repeat(100) { manager.setForeground(session, true) }
            assertEquals(1, player.startAttempts)
            runCurrent()
            advanceTimeBy(999)
            assertEquals(1, player.startAttempts)
            player.failStarts = false
            advanceTimeBy(1)
            runCurrent()
            assertTrue(player.isMusicPlaying)
            repeat(10) { manager.updateSettings(PlayerSettings()) }
            assertEquals(2, player.startAttempts)
        }

    @Test
    fun repeatedFailuresAreBoundedUntilPlaybackIsExplicitlyReenabled() =
        runTest {
            player.failStarts = true
            val manager = manager()
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            repeat(100) { manager.setForeground(session, true) }
            assertEquals(3, player.startAttempts)
            assertFalse(player.isMusicPlaying)
            manager.updateSettings(PlayerSettings(backgroundMusicEnabled = false))
            player.failStarts = false
            manager.updateSettings(PlayerSettings())
            assertTrue(player.isMusicPlaying)
            assertEquals(4, player.startAttempts)
        }

    @Test
    fun asynchronousErrorRecoversAndBackgroundCancelsFurtherRetries() =
        runTest {
            val manager = manager()
            player.failMusic()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, player.startAttempts)
            assertTrue(player.isMusicPlaying)
            player.failMusic()
            manager.setAppForeground(false)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(2, player.startAttempts)
            assertFalse(player.isMusicPlaying)
        }

    @Test
    fun navigationAndSettingsCancelScheduledRetry() =
        runTest {
            player.failStarts = true
            val manager = manager()
            manager.setForeground(session, false)
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, player.startAttempts)
            manager.setForeground(session, true)
            manager.updateSettings(PlayerSettings(backgroundMusicEnabled = false))
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(2, player.startAttempts)
        }
}
