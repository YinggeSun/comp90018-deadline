package com.comp90018.deadline.domain.game.stress

/**
 * Tuning parameters for the Stress System, measured in stress points.
 *
 * Defaults are provisional: the Semester Week difficulty model and Remote Config are
 * expected to supply final numbers later, so gameplay code must read values from a
 * config instance instead of hard-coding them.
 *
 * This type describes only *how much* stress one step changes, whether that step adds stress
 * or removes it through Coffee Recovery. Deciding *when* a step happens — per elapsed second,
 * per tile selection, per completed Coffee triple — belongs to the game engine, which keeps
 * these rules testable without a clock.
 */
data class StressConfig(
    val maximum: Int = DEFAULT_MAXIMUM,
    /**
     * Defaults to the top quarter of [maximum] so that lowering the range alone stays a
     * valid configuration; pass a value explicitly to widen or narrow the High Stress band.
     */
    val highStressThreshold: Int = (maximum.toLong() * 3 / 4).coerceAtLeast(1L).toInt(),
    val degradationProbability: Double = DEFAULT_DEGRADATION_PROBABILITY,
    val baseRate: Int = DEFAULT_BASE_RATE,
    val rateGrowthPerWeek: Int = DEFAULT_RATE_GROWTH_PER_WEEK,
    val coffeeRecoveryBase: Int = DEFAULT_COFFEE_RECOVERY_BASE,
    val coffeeRecoveryDeclinePerWeek: Int = DEFAULT_COFFEE_RECOVERY_DECLINE_PER_WEEK
) {
    init {
        require(maximum > 0) {
            "Maximum stress must be positive."
        }
        require(highStressThreshold in 1..maximum) {
            "High stress threshold must be within 1..maximum."
        }
        require(degradationProbability in 0.0..1.0) {
            "Degradation probability must be within 0.0..1.0."
        }
        require(baseRate >= 0) {
            "Base stress rate must be non-negative."
        }
        require(rateGrowthPerWeek >= 0) {
            "Stress rate growth must be non-negative."
        }
        require(coffeeRecoveryBase >= 0) {
            "Coffee recovery base must be non-negative."
        }
        require(coffeeRecoveryDeclinePerWeek >= 0) {
            "Coffee recovery decline must be non-negative."
        }
    }

    /**
     * Stress added by one accumulation step during [week], growing linearly from [baseRate]
     * to model a semester that gets busier. Week numbering starts at one, matching
     * [com.comp90018.deadline.domain.level.model.WeekConfig].
     *
     * A single step never adds more than the full range, so a large week cannot overflow.
     */
    fun rateForWeek(week: Int): Int {
        require(week > 0) {
            "Week must be positive."
        }
        val rate = baseRate.toLong() + rateGrowthPerWeek.toLong() * (week - 1L)
        return rate.coerceAtMost(maximum.toLong()).toInt()
    }

    /**
     * Stress removed by one Coffee Recovery during [week]. It shrinks linearly from
     * [coffeeRecoveryBase] as the semester gets busier, mirroring how [rateForWeek] grows,
     * so a late-semester coffee buys less relief than an early-semester one. Week numbering
     * starts at one, matching [com.comp90018.deadline.domain.level.model.WeekConfig].
     *
     * The result never leaves `0..`[maximum]: a late week cannot make recovery negative, and
     * a base above the range cannot restore more than the full range.
     */
    fun coffeeRecoveryForWeek(week: Int): Int {
        require(week > 0) {
            "Week must be positive."
        }
        val recovery =
            coffeeRecoveryBase.toLong() - coffeeRecoveryDeclinePerWeek.toLong() * (week - 1L)
        return recovery.coerceIn(0L, maximum.toLong()).toInt()
    }

    companion object {
        const val DEFAULT_MAXIMUM = 100

        /** One in four selections slips while in High Stress. */
        const val DEFAULT_DEGRADATION_PROBABILITY = 0.25

        const val DEFAULT_BASE_RATE = 2

        const val DEFAULT_RATE_GROWTH_PER_WEEK = 1

        /** A Coffee triple undoes roughly ten accumulation steps of the first week. */
        const val DEFAULT_COFFEE_RECOVERY_BASE = 20

        const val DEFAULT_COFFEE_RECOVERY_DECLINE_PER_WEEK = 1
    }
}
