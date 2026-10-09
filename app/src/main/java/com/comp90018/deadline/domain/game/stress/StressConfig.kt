package com.comp90018.deadline.domain.game.stress

/**
 * Tuning parameters for the Stress System, measured in stress points.
 *
 * Defaults are provisional: the Semester Week difficulty model and Remote Config are
 * expected to supply final numbers later, so gameplay code must read values from a
 * config instance instead of hard-coding them.
 *
 * Stress builds up with active play time at [accumulationPercentPerSecond] of [maximum] per
 * second; tile selections never add stress. A completed Coffee triple removes stress. Deciding
 * *when* time has passed belongs to the caller that drives the engine, which keeps these rules
 * testable without a clock.
 *
 * A completed Music triple removes a smaller [musicRecovery] and, for [musicSlowdownMillis] of
 * play, multiplies accumulation by [musicSlowdownMultiplier].
 *
 * [highStressThreshold] only marks the warning band of the gauge. The Maximum Stress state,
 * which makes selections unreliable, starts when stress reaches [maximum].
 */
data class StressConfig(
    val maximum: Int = DEFAULT_MAXIMUM,
    /**
     * Defaults to the top quarter of [maximum] so that lowering the range alone stays a
     * valid configuration; pass a value explicitly to widen or narrow the High Stress band.
     */
    val highStressThreshold: Int = (maximum.toLong() * 3 / 4).coerceAtLeast(1L).toInt(),
    val degradationProbability: Double = DEFAULT_DEGRADATION_PROBABILITY,
    val accumulationPercentPerSecond: Double = DEFAULT_ACCUMULATION_PERCENT_PER_SECOND,
    val coffeeRecoveryBase: Int = DEFAULT_COFFEE_RECOVERY_BASE,
    val coffeeRecoveryDeclinePerWeek: Int = DEFAULT_COFFEE_RECOVERY_DECLINE_PER_WEEK,
    val musicRecovery: Int = DEFAULT_MUSIC_RECOVERY,
    val musicSlowdownMultiplier: Double = DEFAULT_MUSIC_SLOWDOWN_MULTIPLIER,
    val musicSlowdownMillis: Long = DEFAULT_MUSIC_SLOWDOWN_MILLIS,
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
        require(accumulationPercentPerSecond.isFinite() && accumulationPercentPerSecond >= 0.0) {
            "Stress accumulation rate must be finite and non-negative."
        }
        require(coffeeRecoveryBase >= 0) {
            "Coffee recovery base must be non-negative."
        }
        require(coffeeRecoveryDeclinePerWeek >= 0) {
            "Coffee recovery decline must be non-negative."
        }
        require(musicRecovery in 0..maximum) {
            "Music recovery must be within 0..maximum."
        }
        require(musicSlowdownMultiplier in 0.0..1.0) {
            "Music slowdown multiplier must be within 0.0..1.0."
        }
        require(musicSlowdownMillis >= 0L) {
            "Music slowdown duration must be non-negative."
        }
    }

    /** Stress points added per second of active play. */
    val accumulationPerSecond: Double
        get() = maximum * accumulationPercentPerSecond / 100.0

    /**
     * Stress removed by one Coffee Recovery during [week]. It shrinks linearly from
     * [coffeeRecoveryBase] as the semester gets busier, so a late-semester coffee buys less
     * relief than an early-semester one. Week numbering starts at one, matching [com.comp90018.deadline.domain.level.model.WeekConfig].
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

        /** One in five selections slips while at Maximum Stress. */
        const val DEFAULT_DEGRADATION_PROBABILITY = 0.20

        /** An idle game reaches Maximum Stress in a little over a minute. */
        const val DEFAULT_ACCUMULATION_PERCENT_PER_SECOND = 1.5

        /** A first-week Coffee triple undoes roughly thirteen seconds of accumulation. */
        const val DEFAULT_COFFEE_RECOVERY_BASE = 20

        const val DEFAULT_COFFEE_RECOVERY_DECLINE_PER_WEEK = 1

        /** Smaller than a Coffee triple: Music trades immediate relief for a slowdown. */
        const val DEFAULT_MUSIC_RECOVERY = 8

        /** Stress builds at half speed while the Music effect lasts. */
        const val DEFAULT_MUSIC_SLOWDOWN_MULTIPLIER = 0.5

        const val DEFAULT_MUSIC_SLOWDOWN_MILLIS = 8_000L
    }
}
