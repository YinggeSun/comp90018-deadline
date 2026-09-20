package com.comp90018.deadline.sensor.tilt

import kotlin.math.abs

/**
 * Converts pitch (degrees) to a continuous 0..1 peek amount for issue #26.
 * Deadzone prevents accidental activation; smoothing and hysteresis reduce flicker.
 */
class TiltProcessor(
    private val deadzoneDegrees: Float = 8f,
    private val fullPeekDegrees: Float = 32f,
    private val smoothing: Float = 0.18f,
    private val releaseHysteresisDegrees: Float = 2f,
) {
    private var filteredPitch = 0f
    private var active = false

    fun processPitch(rawPitchDegrees: Float): Float {
        filteredPitch += smoothing * (rawPitchDegrees - filteredPitch)
        val magnitude = abs(filteredPitch)

        if (!active && magnitude <= deadzoneDegrees) return 0f
        if (active && magnitude <= deadzoneDegrees - releaseHysteresisDegrees) {
            active = false
            return 0f
        }
        if (magnitude > deadzoneDegrees) active = true

        return ((magnitude - deadzoneDegrees) / (fullPeekDegrees - deadzoneDegrees))
            .coerceIn(0f, 1f)
    }

    fun reset() {
        filteredPitch = 0f
        active = false
    }
}
