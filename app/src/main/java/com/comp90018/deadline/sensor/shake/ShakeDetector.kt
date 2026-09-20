package com.comp90018.deadline.sensor.shake

import kotlin.math.sqrt

/**
 * Pure shake detector for issue #24.
 * Uses acceleration magnitude after removing gravity, exponential smoothing,
 * consecutive hits and cooldown to reduce false positives.
 */
class ShakeDetector(
    private val thresholdG: Float = 1.7f,
    private val requiredHits: Int = 2,
    private val cooldownMillis: Long = 900L,
    private val smoothing: Float = 0.25f,
) {
    private var filteredLinearG = 0f
    private var hitCount = 0
    private var lastTriggerMillis = Long.MIN_VALUE / 2

    fun processAccelerometer(
        x: Float,
        y: Float,
        z: Float,
        nowMillis: Long,
    ): Boolean {
        val magnitude = sqrt(x * x + y * y + z * z)
        val linearG = kotlin.math.abs(magnitude / GRAVITY - 1f)
        filteredLinearG += smoothing * (linearG - filteredLinearG)

        if (filteredLinearG >= thresholdG) {
            hitCount++
        } else {
            hitCount = 0
        }

        val cooledDown = nowMillis - lastTriggerMillis >= cooldownMillis
        if (hitCount >= requiredHits && cooledDown) {
            lastTriggerMillis = nowMillis
            hitCount = 0
            return true
        }
        return false
    }

    fun reset() {
        filteredLinearG = 0f
        hitCount = 0
        lastTriggerMillis = Long.MIN_VALUE / 2
    }

    private companion object {
        const val GRAVITY = 9.80665f
    }
}
