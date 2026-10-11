package com.comp90018.deadline.data.config

import com.comp90018.deadline.domain.config.GameBalance
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.sensor.shake.ShakeConfig
import com.comp90018.deadline.sensor.tilt.TiltConfig

/** The result of reading Remote Config: the balance to use, and the keys that were refused. */
data class RemoteBalanceResult(
    val balance: GameBalance,
    val rejectedKeys: Set<String>,
)

/**
 * Turns Remote Config values (all strings) into a [GameBalance]. Every value is parsed and
 * range-checked before use: a missing or invalid value keeps that setting's default, and if the
 * accepted values of one group cannot form a valid config together (for example a full-peek
 * angle below the deadzone), that whole group keeps its defaults. Nothing here throws.
 */
object RemoteBalance {
    const val STRESS_MAXIMUM = "stress_maximum"
    const val STRESS_HIGH_THRESHOLD = "stress_high_threshold"
    const val STRESS_DEGRADATION_PROBABILITY = "stress_degradation_probability"
    const val STRESS_ACCUMULATION_PERCENT_PER_SECOND = "stress_accumulation_percent_per_second"
    const val COFFEE_RECOVERY_BASE = "coffee_recovery_base"
    const val COFFEE_RECOVERY_DECLINE_PER_WEEK = "coffee_recovery_decline_per_week"
    const val MUSIC_RECOVERY = "music_recovery"
    const val MUSIC_SLOWDOWN_MULTIPLIER = "music_slowdown_multiplier"
    const val MUSIC_SLOWDOWN_MILLIS = "music_slowdown_millis"
    const val SHAKE_THRESHOLD_G = "shake_threshold_g"
    const val SHAKE_REQUIRED_HITS = "shake_required_hits"
    const val SHAKE_COOLDOWN_MILLIS = "shake_cooldown_millis"
    const val SHAKE_SMOOTHING = "shake_smoothing"
    const val TILT_DEADZONE_DEGREES = "tilt_deadzone_degrees"
    const val TILT_FULL_PEEK_DEGREES = "tilt_full_peek_degrees"
    const val TILT_SMOOTHING = "tilt_smoothing"
    const val TILT_RELEASE_HYSTERESIS_DEGREES = "tilt_release_hysteresis_degrees"

    private val stressKeys =
        setOf(
            STRESS_MAXIMUM,
            STRESS_HIGH_THRESHOLD,
            STRESS_DEGRADATION_PROBABILITY,
            STRESS_ACCUMULATION_PERCENT_PER_SECOND,
            COFFEE_RECOVERY_BASE,
            COFFEE_RECOVERY_DECLINE_PER_WEEK,
            MUSIC_RECOVERY,
            MUSIC_SLOWDOWN_MULTIPLIER,
            MUSIC_SLOWDOWN_MILLIS,
        )
    private val shakeKeys = setOf(SHAKE_THRESHOLD_G, SHAKE_REQUIRED_HITS, SHAKE_COOLDOWN_MILLIS, SHAKE_SMOOTHING)
    private val tiltKeys = setOf(TILT_DEADZONE_DEGREES, TILT_FULL_PEEK_DEGREES, TILT_SMOOTHING, TILT_RELEASE_HYSTERESIS_DEGREES)

    /** Every key the app reads, for setting Remote Config defaults and the console template. */
    val KEYS: Set<String> = stressKeys + shakeKeys + tiltKeys

    /** The compiled-in default of every key, as Remote Config strings. */
    fun defaults(): Map<String, String> = toValues(GameBalance())

    fun toValues(balance: GameBalance): Map<String, String> =
        with(balance) {
            mapOf(
                STRESS_MAXIMUM to stress.maximum.toString(),
                STRESS_HIGH_THRESHOLD to stress.highStressThreshold.toString(),
                STRESS_DEGRADATION_PROBABILITY to stress.degradationProbability.toString(),
                STRESS_ACCUMULATION_PERCENT_PER_SECOND to stress.accumulationPercentPerSecond.toString(),
                COFFEE_RECOVERY_BASE to stress.coffeeRecoveryBase.toString(),
                COFFEE_RECOVERY_DECLINE_PER_WEEK to stress.coffeeRecoveryDeclinePerWeek.toString(),
                MUSIC_RECOVERY to stress.musicRecovery.toString(),
                MUSIC_SLOWDOWN_MULTIPLIER to stress.musicSlowdownMultiplier.toString(),
                MUSIC_SLOWDOWN_MILLIS to stress.musicSlowdownMillis.toString(),
                SHAKE_THRESHOLD_G to shake.thresholdG.toString(),
                SHAKE_REQUIRED_HITS to shake.requiredHits.toString(),
                SHAKE_COOLDOWN_MILLIS to shake.cooldownMillis.toString(),
                SHAKE_SMOOTHING to shake.smoothing.toString(),
                TILT_DEADZONE_DEGREES to tilt.deadzoneDegrees.toString(),
                TILT_FULL_PEEK_DEGREES to tilt.fullPeekDegrees.toString(),
                TILT_SMOOTHING to tilt.smoothing.toString(),
                TILT_RELEASE_HYSTERESIS_DEGREES to tilt.releaseHysteresisDegrees.toString(),
            )
        }

    fun read(values: Map<String, String>): RemoteBalanceResult {
        val rejected = mutableSetOf<String>()

        fun int(
            key: String,
            range: IntRange,
            default: Int,
        ): Int = values[key]?.trim()?.toIntOrNull()?.takeIf { it in range } ?: default.also { if (key in values) rejected += key }

        fun long(
            key: String,
            range: LongRange,
            default: Long,
        ): Long = values[key]?.trim()?.toLongOrNull()?.takeIf { it in range } ?: default.also { if (key in values) rejected += key }

        fun double(
            key: String,
            range: ClosedFloatingPointRange<Double>,
            default: Double,
        ): Double =
            values[key]?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it in range }
                ?: default.also { if (key in values) rejected += key }

        fun float(
            key: String,
            range: ClosedFloatingPointRange<Float>,
            default: Float,
        ): Float =
            values[key]?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && it in range }
                ?: default.also { if (key in values) rejected += key }

        val defaults = GameBalance()

        val stress =
            group(stressKeys, rejected, defaults.stress) {
                val maximum = int(STRESS_MAXIMUM, 10..1_000, defaults.stress.maximum)
                StressConfig(
                    maximum = maximum,
                    highStressThreshold = int(STRESS_HIGH_THRESHOLD, 1..1_000, defaults.stress.highStressThreshold),
                    degradationProbability = double(STRESS_DEGRADATION_PROBABILITY, 0.0..1.0, defaults.stress.degradationProbability),
                    accumulationPercentPerSecond =
                        double(STRESS_ACCUMULATION_PERCENT_PER_SECOND, 0.0..20.0, defaults.stress.accumulationPercentPerSecond),
                    coffeeRecoveryBase = int(COFFEE_RECOVERY_BASE, 0..1_000, defaults.stress.coffeeRecoveryBase),
                    coffeeRecoveryDeclinePerWeek =
                        int(
                            COFFEE_RECOVERY_DECLINE_PER_WEEK,
                            0..100,
                            defaults.stress.coffeeRecoveryDeclinePerWeek,
                        ),
                    musicRecovery = int(MUSIC_RECOVERY, 0..1_000, defaults.stress.musicRecovery),
                    musicSlowdownMultiplier = double(MUSIC_SLOWDOWN_MULTIPLIER, 0.0..1.0, defaults.stress.musicSlowdownMultiplier),
                    musicSlowdownMillis = long(MUSIC_SLOWDOWN_MILLIS, 0L..60_000L, defaults.stress.musicSlowdownMillis),
                )
            }
        val shake =
            group(shakeKeys, rejected, defaults.shake) {
                ShakeConfig(
                    thresholdG = float(SHAKE_THRESHOLD_G, 0.5f..5f, defaults.shake.thresholdG),
                    requiredHits = int(SHAKE_REQUIRED_HITS, 1..10, defaults.shake.requiredHits),
                    cooldownMillis = long(SHAKE_COOLDOWN_MILLIS, 0L..10_000L, defaults.shake.cooldownMillis),
                    smoothing = float(SHAKE_SMOOTHING, 0.01f..1f, defaults.shake.smoothing),
                )
            }
        val tilt =
            group(tiltKeys, rejected, defaults.tilt) {
                TiltConfig(
                    deadzoneDegrees = float(TILT_DEADZONE_DEGREES, 0f..45f, defaults.tilt.deadzoneDegrees),
                    fullPeekDegrees = float(TILT_FULL_PEEK_DEGREES, 1f..90f, defaults.tilt.fullPeekDegrees),
                    smoothing = float(TILT_SMOOTHING, 0.01f..1f, defaults.tilt.smoothing),
                    releaseHysteresisDegrees = float(TILT_RELEASE_HYSTERESIS_DEGREES, 0f..45f, defaults.tilt.releaseHysteresisDegrees),
                )
            }
        return RemoteBalanceResult(GameBalance(stress, shake, tilt), rejected)
    }

    /** Builds one group; if its values cannot form a valid config together, keeps the group's defaults. */
    private fun <T> group(
        keys: Set<String>,
        rejected: MutableSet<String>,
        default: T,
        build: () -> T,
    ): T =
        try {
            build()
        } catch (invalidCombination: IllegalArgumentException) {
            rejected += keys
            default
        }
}
