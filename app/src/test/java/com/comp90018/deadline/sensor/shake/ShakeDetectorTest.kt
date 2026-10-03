package com.comp90018.deadline.sensor.shake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {
    @Test
    fun normalGravity_doesNotTrigger() {
        val detector = ShakeDetector(smoothing = 1f)
        repeat(20) { i ->
            assertFalse(detector.processAccelerometer(0f, 0f, 9.80665f, i * 20L))
        }
    }

    @Test
    fun strongConsecutiveMovement_triggersOnceDuringCooldown() {
        val detector = ShakeDetector(
            thresholdG = 1.2f,
            requiredHits = 2,
            cooldownMillis = 900L,
            smoothing = 1f,
        )

        assertFalse(detector.processAccelerometer(30f, 0f, 0f, 1000L))
        assertTrue(detector.processAccelerometer(30f, 0f, 0f, 1020L))
        assertFalse(detector.processAccelerometer(30f, 0f, 0f, 1040L))
        assertFalse(detector.processAccelerometer(30f, 0f, 0f, 1060L))
    }
}
