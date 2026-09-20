package com.comp90018.deadline.sensor.tilt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltProcessorTest {
    @Test
    fun insideDeadzone_returnsZero() {
        val processor = TiltProcessor(smoothing = 1f)
        assertEquals(0f, processor.processPitch(4f), 0.0001f)
    }

    @Test
    fun increasingTilt_producesContinuousPeek() {
        val processor = TiltProcessor(smoothing = 1f)
        val low = processor.processPitch(12f)
        val high = processor.processPitch(24f)

        assertTrue(low > 0f)
        assertTrue(high > low)
        assertTrue(high <= 1f)
    }

    @Test
    fun largeTilt_isClampedToOne() {
        val processor = TiltProcessor(smoothing = 1f)
        assertEquals(1f, processor.processPitch(60f), 0.0001f)
    }
}
