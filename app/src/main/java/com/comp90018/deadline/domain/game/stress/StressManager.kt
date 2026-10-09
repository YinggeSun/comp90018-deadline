package com.comp90018.deadline.domain.game.stress

/**
 * Pure stress rules. Every operation takes the current value and returns the next one, so
 * stress can be stored in the game state without this type holding session state of its own.
 *
 * Every returned value lies within `0..`[StressConfig.maximum], including when a caller
 * passes a value that is already out of range.
 */
class StressManager(val config: StressConfig = StressConfig()) {
    /**
     * Stress points that [elapsedMillis] of active play adds, before rounding. Callers carry
     * the fractional part between ticks so a fast tick rate does not lose accumulation.
     */
    fun accumulationFor(elapsedMillis: Long): Double {
        require(elapsedMillis >= 0L) {
            "Elapsed time must be non-negative."
        }
        return config.accumulationPerSecond * elapsedMillis / MILLIS_PER_SECOND
    }

    /** Stress after adding [amount], saturating at [StressConfig.maximum]. */
    fun increaseBy(
        current: Int,
        amount: Int,
    ): Int {
        require(amount >= 0) {
            "Stress increase must be non-negative."
        }
        return clampToRange(current.toLong() + amount.toLong())
    }

    /**
     * Stress after removing [amount], never falling below zero. Coffee Recovery decides the
     * recovery amount; this method only enforces the lower bound.
     */
    fun decreaseBy(
        current: Int,
        amount: Int,
    ): Int {
        require(amount >= 0) {
            "Stress decrease must be non-negative."
        }
        return clampToRange(current.toLong() - amount.toLong())
    }

    /** True once stress reaches [StressConfig.highStressThreshold], the gauge's warning band. */
    fun isHighStress(current: Int): Boolean = clamp(current) >= config.highStressThreshold

    /**
     * True while stress sits at [StressConfig.maximum]. This is the Maximum Stress state that
     * drives input degradation and the flashing and haptic warnings.
     */
    fun isMaxStress(current: Int): Boolean = clamp(current) >= config.maximum

    /** Brings any value, including out-of-range stored state, back into the valid range. */
    fun clamp(current: Int): Int = current.coerceIn(0, config.maximum)

    private fun clampToRange(value: Long): Int = value.coerceIn(0L, config.maximum.toLong()).toInt()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
    }
}
