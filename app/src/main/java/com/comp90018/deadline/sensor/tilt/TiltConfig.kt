package com.comp90018.deadline.sensor.tilt

/**
 * Tuning for [TiltProcessor]: pitch below [deadzoneDegrees] does nothing, the peek is full at
 * [fullPeekDegrees], [smoothing] is the filter factor, and once active the peek only releases
 * [releaseHysteresisDegrees] below the deadzone, to avoid flicker.
 * Defaults are the values tuned on devices; Remote Config can replace them (#52).
 */
data class TiltConfig(
    val deadzoneDegrees: Float = DEFAULT_DEADZONE_DEGREES,
    val fullPeekDegrees: Float = DEFAULT_FULL_PEEK_DEGREES,
    val smoothing: Float = DEFAULT_SMOOTHING,
    val releaseHysteresisDegrees: Float = DEFAULT_RELEASE_HYSTERESIS_DEGREES,
) {
    init {
        require(deadzoneDegrees.isFinite() && deadzoneDegrees >= 0f) { "Tilt deadzone must be non-negative." }
        require(fullPeekDegrees.isFinite() && fullPeekDegrees > deadzoneDegrees && fullPeekDegrees <= 90f) {
            "Full peek must be above the deadzone and at most 90 degrees."
        }
        require(smoothing.isFinite() && smoothing > 0f && smoothing <= 1f) { "Tilt smoothing must be within (0, 1]." }
        require(releaseHysteresisDegrees.isFinite() && releaseHysteresisDegrees in 0f..deadzoneDegrees) {
            "Release hysteresis must be within 0..deadzone."
        }
    }

    companion object {
        const val DEFAULT_DEADZONE_DEGREES = 8f
        const val DEFAULT_FULL_PEEK_DEGREES = 32f
        const val DEFAULT_SMOOTHING = 0.18f
        const val DEFAULT_RELEASE_HYSTERESIS_DEGREES = 2f
    }
}
