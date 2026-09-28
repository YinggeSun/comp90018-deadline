package com.comp90018.deadline.domain.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionTimerTest {
    private var now = 0L
    private val timer = CompletionTimer { now }

    @Test
    fun freshTimerIsStoppedAtZero() {
        assertFalse(timer.isRunning)
        assertEquals(0L, timer.elapsedMillis)
        assertEquals(0L, timer.elapsedSeconds)
    }

    @Test
    fun timeBeforeTheLevelStartsIsNotCounted() {
        advanceMillis(5_000)

        assertEquals(0L, timer.elapsedMillis)
    }

    @Test
    fun elapsedGrowsWhileRunning() {
        timer.start()

        advanceMillis(1_200)
        assertEquals(1_200L, timer.elapsedMillis)

        advanceMillis(800)
        assertEquals(2_000L, timer.elapsedMillis)
        assertTrue(timer.isRunning)
    }

    @Test
    fun completionFreezesElapsedSoItNeverKeepsCounting() {
        timer.start()
        advanceMillis(3_000)

        timer.stop()
        advanceMillis(9_000)

        assertFalse(timer.isRunning)
        assertEquals(3_000L, timer.elapsedMillis)
    }

    @Test
    fun stoppingAnAlreadyStoppedTimerKeepsTheFrozenValue() {
        timer.start()
        advanceMillis(2_500)
        timer.stop()

        advanceMillis(4_000)
        timer.stop()
        advanceMillis(4_000)

        assertEquals(2_500L, timer.elapsedMillis)
    }

    @Test
    fun startingAnAlreadyRunningTimerNeitherRestartsNorDoubleCounts() {
        timer.start()
        advanceMillis(1_000)

        timer.start()
        advanceMillis(1_000)

        assertEquals(2_000L, timer.elapsedMillis)
    }

    @Test
    fun resetReturnsToZeroAndStopsCounting() {
        timer.start()
        advanceMillis(7_000)

        timer.reset()

        assertFalse(timer.isRunning)
        assertEquals(0L, timer.elapsedMillis)

        advanceMillis(3_000)
        assertEquals(0L, timer.elapsedMillis)
    }

    @Test
    fun restartReturnsToZeroAndKeepsCounting() {
        timer.start()
        advanceMillis(7_000)

        timer.restart()

        assertTrue(timer.isRunning)
        assertEquals(0L, timer.elapsedMillis)

        advanceMillis(1_500)
        assertEquals(1_500L, timer.elapsedMillis)
    }

    @Test
    fun restartAfterCompletionDiscardsThePreviousAttempt() {
        timer.start()
        advanceMillis(4_000)
        timer.stop()

        timer.restart()
        advanceMillis(600)

        assertEquals(600L, timer.elapsedMillis)
    }

    @Test
    fun resumingAfterAStopKeepsTheTimeAlreadyPlayed() {
        timer.start()
        advanceMillis(1_000)
        timer.stop()

        advanceMillis(60_000)
        timer.start()
        advanceMillis(500)

        assertEquals(1_500L, timer.elapsedMillis)
    }

    @Test
    fun elapsedTruncatesTowardsZero() {
        timer.start()
        advanceNanos(1_999_999)

        assertEquals(1L, timer.elapsedMillis)
        assertEquals(0L, timer.elapsedSeconds)

        advanceNanos(1_000_000_000 - 1_999_999)
        assertEquals(1_000L, timer.elapsedMillis)
        assertEquals(1L, timer.elapsedSeconds)
    }

    @Test
    fun aSourceThatGoesBackwardsCannotProduceNegativeElapsed() {
        timer.start()
        advanceMillis(2_000)

        now -= 5_000 * NANOS_PER_MILLISECOND

        assertEquals(0L, timer.elapsedMillis)
        assertTrue(timer.elapsedMillis >= 0L)
    }

    @Test
    fun timeBankedBeforeANonMonotonicJumpIsStillKept() {
        timer.start()
        advanceMillis(2_000)
        timer.stop()

        now -= 5_000 * NANOS_PER_MILLISECOND
        timer.start()

        assertEquals(2_000L, timer.elapsedMillis)
    }

    @Test
    fun theDefaultSourceIsAWorkingMonotonicClock() {
        val realTimer = CompletionTimer()

        realTimer.start()
        assertTrue(realTimer.isRunning)
        assertTrue(realTimer.elapsedMillis >= 0L)

        realTimer.stop()
        val frozen = realTimer.elapsedMillis
        assertEquals(frozen, realTimer.elapsedMillis)
    }

    private fun advanceMillis(millis: Long) = advanceNanos(millis * NANOS_PER_MILLISECOND)

    private fun advanceNanos(nanos: Long) {
        now += nanos
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
