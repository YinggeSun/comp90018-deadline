package com.comp90018.deadline.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundEffectQueueTest {
    private val played = mutableListOf<GameAudioEvent>()
    private val queue = SoundEffectQueue { played.add(it) }

    @Test
    fun coldRequestsPlayOnceWhenReadyIncludingAfterGameNavigation() {
        queue.loading(GameAudioEvent.MATCH)
        queue.play(GameAudioEvent.MATCH)
        assertTrue(played.isEmpty())
        // Destination navigation leaves the application-owned queue intact.
        queue.loaded(GameAudioEvent.MATCH, true)
        queue.loaded(GameAudioEvent.MATCH, true)
        assertEquals(listOf(GameAudioEvent.MATCH), played)
    }

    @Test
    fun failedSampleCannotFillQueueOrBlockOtherSamples() {
        queue.loading(GameAudioEvent.TILE_CLICK)
        queue.play(GameAudioEvent.TILE_CLICK)
        queue.loaded(GameAudioEvent.TILE_CLICK, false)
        repeat(100) { queue.play(GameAudioEvent.TILE_CLICK) }
        queue.loading(GameAudioEvent.MATCH)
        queue.play(GameAudioEvent.MATCH)
        queue.loaded(GameAudioEvent.MATCH, true)
        assertEquals(listOf(GameAudioEvent.MATCH), played)
    }

    @Test
    fun pendingRequestsAreBoundedAndStopDropsThem() {
        queue.loading(GameAudioEvent.TILE_CLICK)
        repeat(100) { queue.play(GameAudioEvent.TILE_CLICK) }
        queue.loaded(GameAudioEvent.TILE_CLICK, true)
        assertEquals(16, played.size)
        queue.loading(GameAudioEvent.MATCH)
        queue.play(GameAudioEvent.MATCH)
        queue.stop()
        queue.loaded(GameAudioEvent.MATCH, true)
        assertEquals(16, played.size)
    }

    @Test
    fun failedLoadCanRecoverAfterRecreationWithoutReplayingOldRequests() {
        queue.loading(GameAudioEvent.MATCH)
        queue.play(GameAudioEvent.MATCH)
        queue.loaded(GameAudioEvent.MATCH, false)
        queue.reset()
        queue.loaded(GameAudioEvent.MATCH, true)
        assertTrue(played.isEmpty())
        queue.loading(GameAudioEvent.MATCH)
        queue.play(GameAudioEvent.MATCH)
        queue.loaded(GameAudioEvent.MATCH, true)
        assertEquals(listOf(GameAudioEvent.MATCH), played)
    }
}
