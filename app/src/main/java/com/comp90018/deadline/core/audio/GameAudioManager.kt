package com.comp90018.deadline.core.audio

import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GameAudioEvent { TILE_CLICK, MATCH, STRESS_MAX }

/** One-shot events and session visibility; called on the main thread. */
interface GameAudio {
    fun setForeground(
        session: Any,
        foreground: Boolean,
        gameplayActive: Boolean = true,
    )

    fun play(
        session: Any,
        event: GameAudioEvent,
    )

    fun release(session: Any)

    object None : GameAudio {
        override fun setForeground(
            session: Any,
            foreground: Boolean,
            gameplayActive: Boolean,
        ) = Unit

        override fun play(
            session: Any,
            event: GameAudioEvent,
        ) = Unit

        override fun release(session: Any) = Unit
    }
}

/** Platform boundary. All methods and error callbacks run on the main thread. */
interface AudioPlayer {
    val isMusicPlaying: Boolean
    var onMusicError: (() -> Unit)?

    fun prepare()

    fun startMusic(): Boolean

    fun pauseMusic()

    fun releaseMusic()

    fun play(event: GameAudioEvent)

    fun stopEffects()

    fun releaseEffects()

    fun release()
}

/**
 * One application-owned player. Gameplay owns BGM; the visible app owns short effects so a
 * completed match survives navigation. Backgrounding releases both, including pending loads.
 */
class GameAudioManager(
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
) : GameAudio {
    private var session: Any? = null
    private var foreground = false
    private var appForeground = false
    private var gameplayActive = false
    private var settings: PlayerSettings? = null
    private var musicAttempts = 0
    private var retry: Job? = null

    init {
        player.onMusicError = { scheduleRetry() }
    }

    /** Activity visibility, independent of the navigation destination. */
    fun setAppForeground(foreground: Boolean) {
        if (appForeground == foreground) return
        appForeground = foreground
        if (!foreground) {
            cancelRetry()
            player.release()
        } else {
            updatePlayback()
        }
    }

    fun updateSettings(settings: PlayerSettings) {
        val effectsWereEnabled = this.settings?.soundEffectsEnabled
        this.settings = settings
        if (!settings.soundEffectsEnabled && effectsWereEnabled != false) player.releaseEffects()
        updatePlayback()
    }

    override fun setForeground(
        session: Any,
        foreground: Boolean,
        gameplayActive: Boolean,
    ) {
        if (foreground && this.session !== session) {
            // A new session takes ownership; callbacks from the old ViewModel cannot clear it.
            cancelRetry()
            player.releaseMusic()
            player.stopEffects()
            this.session = session
        }
        if (this.session !== session) return
        this.foreground = foreground
        this.gameplayActive = gameplayActive
        if (!foreground && gameplayActive) player.stopEffects()
        updatePlayback()
    }

    private fun wantsMusic() = appForeground && foreground && gameplayActive && settings?.backgroundMusicEnabled == true

    private fun updatePlayback() {
        if (appForeground && foreground && settings?.soundEffectsEnabled == true) player.prepare()
        if (!wantsMusic()) {
            cancelRetry()
            player.pauseMusic()
        } else if (!player.isMusicPlaying && retry == null && musicAttempts < MAX_MUSIC_ATTEMPTS) {
            musicAttempts++
            if (!player.startMusic()) scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (!wantsMusic() || retry != null || musicAttempts >= MAX_MUSIC_ATTEMPTS) return
        retry =
            scope.launch {
                delay(RETRY_DELAY_MILLIS)
                retry = null
                updatePlayback()
            }
    }

    private fun cancelRetry() {
        retry?.cancel()
        retry = null
        musicAttempts = 0
    }

    override fun play(
        session: Any,
        event: GameAudioEvent,
    ) {
        if (this.session === session && appForeground && foreground && settings?.soundEffectsEnabled == true) player.play(event)
    }

    override fun release(session: Any) {
        if (this.session !== session) return
        this.session = null
        foreground = false
        cancelRetry()
        player.releaseMusic()
        if (gameplayActive) player.stopEffects()
        // SoundPool remains application-owned until background/SFX-off. Final effects can finish.
    }

    private companion object {
        const val MAX_MUSIC_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
