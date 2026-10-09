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
     * Stress after one accumulation step during [week]. The caller decides how often a step
     * occurs; see [StressConfig].
     */
    fun accumulate(
        current: Int,
        week: Int,
    ): Int = increaseBy(current, config.rateForWeek(week))

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

    /**
     * True once stress reaches [StressConfig.highStressThreshold]. This is the High Stress
     * state that drives input degradation and the visual and haptic warnings.
     */
    fun isHighStress(current: Int): Boolean = clamp(current) >= config.highStressThreshold

    /** Brings any value, including out-of-range stored state, back into the valid range. */
    fun clamp(current: Int): Int = current.coerceIn(0, config.maximum)

    private fun clampToRange(value: Long): Int = value.coerceIn(0L, config.maximum.toLong()).toInt()
}
