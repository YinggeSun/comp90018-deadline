package com.comp90018.deadline.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.comp90018.deadline.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns Android resources, never an Activity. All access and callbacks run on main. */
class AndroidAudioPlayer(context: Context, private val scope: CoroutineScope) : AudioPlayer {
    private val context = context.applicationContext
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    private var music: MediaPlayer? = null
    private var pool: SoundPool? = null
    private var effectsAttempted = false
    private val samples = mutableMapOf<GameAudioEvent, Int>()
    private val loadTimeouts = mutableMapOf<GameAudioEvent, Job>()
    private val streams = ArrayDeque<Int>()
    private val effects = SoundEffectQueue(::playReady)

    override var onMusicError: (() -> Unit)? = null
    override val isMusicPlaying: Boolean
        get() = runCatching { music?.isPlaying == true }.getOrDefault(false)

    override fun prepare() {
        if (effectsAttempted) return
        effectsAttempted = true
        // One load attempt per pool lifetime. Backgrounding or SFX off/on permits a fresh attempt.
        safely {
            val sounds = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attributes).build()
            pool = sounds
            sounds.setOnLoadCompleteListener { source, sample, status ->
                if (source === pool) {
                    samples.entries.firstOrNull { it.value == sample }?.key?.let { event ->
                        loadTimeouts.remove(event)?.cancel()
                        effects.loaded(event, status == 0)
                        if (status != 0) Log.w("GameAudio", "Could not load $event: $status")
                    }
                }
            }
            load(sounds, GameAudioEvent.TILE_CLICK, R.raw.sfx_tile_click)
            load(sounds, GameAudioEvent.MATCH, R.raw.sfx_match)
            load(sounds, GameAudioEvent.STRESS_MAX, R.raw.sfx_stress_max)
        }
    }

    private fun load(
        sounds: SoundPool,
        event: GameAudioEvent,
        resource: Int,
    ) {
        effects.loading(event)
        val sample = runCatching { sounds.load(context, resource, 1) }.getOrDefault(0)
        samples[event] = sample
        if (sample == 0) {
            effects.loaded(event, false)
        } else {
            loadTimeouts[event] =
                scope.launch {
                    delay(LOAD_TIMEOUT_MILLIS)
                    if (pool === sounds) effects.loaded(event, false)
                    loadTimeouts.remove(event)
                }
        }
    }

    override fun startMusic(): Boolean {
        return try {
            val player =
                music ?: MediaPlayer.create(context, R.raw.bgm_gameplay, attributes, 0)?.also {
                    music = it
                    it.isLooping = true
                    it.setVolume(0.28f, 0.28f)
                    it.setOnErrorListener { failed, _, _ ->
                        if (music === failed) {
                            releaseMusic()
                            onMusicError?.invoke()
                        }
                        true
                    }
                }
            if (player == null) {
                false
            } else {
                if (!player.isPlaying) player.start()
                player.isPlaying
            }
        } catch (error: Exception) {
            Log.w("GameAudio", "Could not start music", error)
            releaseMusic()
            false
        }
    }

    override fun pauseMusic() {
        runCatching { music?.let { if (it.isPlaying) it.pause() } }
            .onFailure { releaseMusic() }
    }

    override fun releaseMusic() {
        val player = music
        music = null
        safely { player?.release() }
    }

    override fun play(event: GameAudioEvent) = effects.play(event)

    private fun playReady(event: GameAudioEvent) {
        val sample = samples[event] ?: return
        safely {
            if (streams.size == MAX_STREAMS) pool?.stop(streams.removeFirst())
            pool?.play(sample, 1f, 1f, 1, 0, 1f)?.takeIf { it != 0 }?.let(streams::addLast)
        }
    }

    override fun stopEffects() {
        effects.stop()
        safely { streams.forEach { pool?.stop(it) } }
        streams.clear()
    }

    override fun releaseEffects() {
        stopEffects()
        loadTimeouts.values.forEach { it.cancel() }
        loadTimeouts.clear()
        val sounds = pool
        pool = null // Ignore any late callback from this pool.
        safely { sounds?.release() }
        samples.clear()
        effects.reset()
        effectsAttempted = false
    }

    override fun release() {
        releaseMusic()
        releaseEffects()
    }

    private inline fun safely(action: () -> Unit) {
        runCatching(action).onFailure { Log.w("GameAudio", "Audio playback unavailable", it) }
    }

    private companion object {
        const val MAX_STREAMS = 8
        const val LOAD_TIMEOUT_MILLIS = 5_000L
    }
}
