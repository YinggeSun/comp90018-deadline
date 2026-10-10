package com.comp90018.deadline.core.audio

/** Readiness policy independent of SoundPool, with a bounded queue and explicit failed samples. */
internal class SoundEffectQueue(private val playReady: (GameAudioEvent) -> Unit) {
    private enum class Status { LOADING, READY, FAILED }

    private val states = mutableMapOf<GameAudioEvent, Status>()
    private val pending = ArrayDeque<GameAudioEvent>()

    fun loading(event: GameAudioEvent) {
        states[event] = Status.LOADING
    }

    fun loaded(
        event: GameAudioEvent,
        success: Boolean,
    ) {
        if (states[event] != Status.LOADING) return
        states[event] = if (success) Status.READY else Status.FAILED
        val ready = pending.filter { it == event }
        pending.removeAll(ready.toSet())
        if (success) ready.forEach(playReady)
    }

    fun play(event: GameAudioEvent) {
        when (states[event]) {
            Status.READY -> playReady(event)
            Status.LOADING -> if (pending.size < MAX_PENDING) pending.addLast(event)
            else -> Unit // Failed samples cannot fill the queue; retry only after pool recreation.
        }
    }

    fun stop() {
        pending.clear()
    }

    fun reset() {
        stop()
        states.clear()
    }

    private companion object {
        const val MAX_PENDING = 16
    }
}
