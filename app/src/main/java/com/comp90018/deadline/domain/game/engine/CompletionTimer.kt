package com.comp90018.deadline.domain.game.engine

/**
 * Measures how long the player takes to clear a level, for the Result screen and Personal Best.
 *
 * The time source is injected so the rules can be tested without sleeping, and so the domain
 * layer stays free of Android APIs. The default is [System.nanoTime], which is monotonic and
 * therefore cannot jump when the device clock is adjusted; it does not advance while the device
 * is in deep sleep. A caller that wants sleep included may inject an Android-backed source.
 * Any injected source must be monotonic; a source that moves backwards yields no elapsed time
 * for that interval instead of a negative one.
 *
 * The engine owns the lifecycle: [start] when a level begins, [stop] once the game reaches a
 * terminal status, and [restart] when the player replays. [stop] freezes the elapsed value, so
 * a completed game never keeps counting.
 */
class CompletionTimer(private val nowNanos: () -> Long = System::nanoTime) {
    /** Reading of [nowNanos] taken when the timer last started; null while stopped. */
    private var resumedAtNanos: Long? = null

    /** Time already banked by previous running periods, excluding the current one. */
    private var bankedNanos: Long = 0L

    val isRunning: Boolean
        get() = resumedAtNanos != null

    /** Elapsed running time in milliseconds, truncated towards zero. */
    val elapsedMillis: Long
        get() = elapsedNanos() / NANOS_PER_MILLISECOND

    /** Convenience for [com.comp90018.deadline.core.util.TimeFormatter.formatSeconds]. */
    val elapsedSeconds: Long
        get() = elapsedNanos() / NANOS_PER_SECOND

    /**
     * Begins or resumes counting. Calling this while already running is a no-op, so a repeated
     * level-start cannot restart the measurement or bank time twice.
     */
    fun start() {
        if (isRunning) return
        resumedAtNanos = nowNanos()
    }

    /**
     * Freezes the elapsed value. Calling this while already stopped is a no-op. A later [start]
     * resumes from the frozen value rather than from zero, which is what lets the game pause
     * without losing the player's time.
     */
    fun stop() {
        val resumedAt = resumedAtNanos ?: return
        bankedNanos += elapsedSince(resumedAt)
        resumedAtNanos = null
    }

    /** Clears all timing state and leaves the timer stopped. */
    fun reset() {
        resumedAtNanos = null
        bankedNanos = 0L
    }

    /** Clears all timing state and immediately begins counting again, for a level replay. */
    fun restart() {
        reset()
        start()
    }

    private fun elapsedNanos(): Long {
        val resumedAt = resumedAtNanos ?: return bankedNanos
        return bankedNanos + elapsedSince(resumedAt)
    }

    /**
     * Clamped at zero so a source that reports a value below the previous reading yields no
     * elapsed time rather than a negative one. A strictly increasing elapsed value is a
     * property of the injected source, not something this class can restore.
     */
    private fun elapsedSince(resumedAt: Long): Long = (nowNanos() - resumedAt).coerceAtLeast(0L)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
