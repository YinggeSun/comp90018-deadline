package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRecoveryTest {
    private val music = MusicRecovery(StressConfig())

    @Test
    fun musicIsTheOnlyTypeThatTriggersTheEffect() {
        assertTrue(music.isRecoveryMatch(TileType.MUSIC))
        for (type in TileType.entries - TileType.MUSIC) assertFalse("$type", music.isRecoveryMatch(type))
        assertFalse(music.isRecoveryMatch(null))
    }

    @Test
    fun musicTripleRemovesTheConfiguredSmallRecovery() {
        assertEquals(42, music.applyMatch(current = 50, matchedType = TileType.MUSIC))
        assertEquals(50, music.applyMatch(current = 50, matchedType = TileType.EXAM))
        assertEquals(50, music.applyMatch(current = 50, matchedType = null))
    }

    @Test
    fun musicRecoveryIsSmallerThanCoffee() {
        val config = StressConfig()

        assertTrue(config.musicRecovery < config.coffeeRecoveryForWeek(1))
    }

    @Test
    fun musicRecoveryStopsAtZero() {
        assertEquals(0, music.applyMatch(current = 3, matchedType = TileType.MUSIC))
    }

    @Test
    fun musicTripleStartsOrRefreshesTheFullSlowdownWithoutStacking() {
        assertEquals(8_000L, music.slowdownAfterMatch(remainingMillis = 0L, matchedType = TileType.MUSIC))
        assertEquals(8_000L, music.slowdownAfterMatch(remainingMillis = 3_000L, matchedType = TileType.MUSIC))
        assertEquals(3_000L, music.slowdownAfterMatch(remainingMillis = 3_000L, matchedType = TileType.COFFEE))
    }

    @Test
    fun slowedTimeAccumulatesAtTheMultipliedRate() {
        assertEquals(1.5, music.accumulationFor(elapsedMillis = 1_000L, remainingMillis = 0L), 1e-9)
        assertEquals(0.75, music.accumulationFor(elapsedMillis = 1_000L, remainingMillis = 8_000L), 1e-9)
    }

    @Test
    fun aTickCrossingTheEndOfTheEffectIsSplitExactly() {
        // 2 s slowed (1.5 points) plus 2 s at the normal rate (3 points).
        assertEquals(4.5, music.accumulationFor(elapsedMillis = 4_000L, remainingMillis = 2_000L), 1e-9)
        assertEquals(0L, music.remainingAfter(elapsedMillis = 4_000L, remainingMillis = 2_000L))
        assertEquals(6_000L, music.remainingAfter(elapsedMillis = 2_000L, remainingMillis = 8_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeElapsedTime() {
        music.accumulationFor(elapsedMillis = -1L, remainingMillis = 0L)
    }
}
