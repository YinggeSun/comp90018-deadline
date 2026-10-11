package com.comp90018.deadline.sensor

import com.comp90018.deadline.sensor.shake.ShakeConfig
import com.comp90018.deadline.sensor.shake.ShakeDetector
import com.comp90018.deadline.sensor.tilt.TiltConfig
import com.comp90018.deadline.sensor.tilt.TiltProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detectors follow the tuning they are given, so Remote Config values take effect (#52). */
class SensorConfigTest {
    /** A reading of [g] linear acceleration along z, on top of gravity. */
    private fun ShakeDetector.read(
        g: Float,
        at: Long,
    ) = processAccelerometer(0f, 0f, (1f + g) * 9.80665f, at)

    @Test
    fun shakeThresholdAndHitsComeFromTheConfig() {
        val sensitive = ShakeDetector(ShakeConfig(thresholdG = 1f, requiredHits = 1, smoothing = 1f))
        val strict = ShakeDetector(ShakeConfig(thresholdG = 3f, requiredHits = 1, smoothing = 1f))

        assertTrue(sensitive.read(g = 2f, at = 0))
        assertFalse(strict.read(g = 2f, at = 0))
    }

    @Test
    fun shakeCooldownComesFromTheConfig() {
        val detector = ShakeDetector(ShakeConfig(requiredHits = 1, smoothing = 1f, cooldownMillis = 2_000))

        assertTrue(detector.read(g = 3f, at = 0))
        assertFalse(detector.read(g = 3f, at = 1_500))
        assertTrue(detector.read(g = 3f, at = 2_000))
    }

    @Test
    fun tiltDeadzoneAndFullPeekComeFromTheConfig() {
        val wide = TiltProcessor(TiltConfig(deadzoneDegrees = 20f, fullPeekDegrees = 40f, smoothing = 1f))
        val narrow = TiltProcessor(TiltConfig(deadzoneDegrees = 5f, fullPeekDegrees = 25f, smoothing = 1f))

        assertEquals(0f, wide.processPitch(15f))
        assertEquals(0.5f, narrow.processPitch(15f), 0.0001f)
    }

    @Test
    fun theDefaultConfigsMatchTheDetectorsDefaults() {
        val readings = listOf(0.5f, 2f, 2.5f, 3f, 0.2f, 3f, 3f)
        val fromDefaults = ShakeDetector()
        val fromConfig = ShakeDetector(ShakeConfig())
        readings.forEachIndexed { i, g -> assertEquals(fromDefaults.read(g, i * 100L), fromConfig.read(g, i * 100L)) }

        val tiltDefaults = TiltProcessor()
        val tiltConfig = TiltProcessor(TiltConfig())
        listOf(0f, 10f, 20f, 35f, 5f).forEach { assertEquals(tiltDefaults.processPitch(it), tiltConfig.processPitch(it)) }
    }

    @Test
    fun invalidTuningIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { ShakeConfig(thresholdG = 0f) }
        assertThrows(IllegalArgumentException::class.java) { ShakeConfig(requiredHits = 0) }
        assertThrows(IllegalArgumentException::class.java) { ShakeConfig(smoothing = 0f) }
        assertThrows(IllegalArgumentException::class.java) { TiltConfig(deadzoneDegrees = 30f, fullPeekDegrees = 20f) }
        assertThrows(IllegalArgumentException::class.java) { TiltConfig(releaseHysteresisDegrees = 10f) }
        assertThrows(IllegalArgumentException::class.java) { TiltConfig(fullPeekDegrees = Float.NaN) }
    }
}
