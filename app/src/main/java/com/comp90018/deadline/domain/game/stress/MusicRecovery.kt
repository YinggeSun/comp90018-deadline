package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.model.TileType

/**
 * Music Recovery rules: clearing a Music triple removes a little stress and slows further
 * accumulation for a while.
 *
 * Like [CoffeeRecovery], Music is an ordinary matchable [TileType]; the engine resolves the
 * triple normally and then asks this class for the new stress value and slowdown time. Every
 * operation takes the current values and returns the next ones, so no session state lives here.
 *
 * A second Music triple refreshes the slowdown to its full duration instead of stacking it,
 * so the multiplier never compounds.
 */
class MusicRecovery(private val config: StressConfig = StressConfig()) {
    private val stressManager = StressManager(config)

    /** True when completing a triple of [matchedType] triggers the Music effect. */
    fun isRecoveryMatch(matchedType: TileType?): Boolean = matchedType == RECOVERY_TYPE

    /** Stress after a selection that completed a triple of [matchedType], or none when null. */
    fun applyMatch(
        current: Int,
        matchedType: TileType?,
    ): Int =
        stressManager.decreaseBy(
            current = current,
            amount = if (isRecoveryMatch(matchedType)) config.musicRecovery else 0,
        )

    /** Slowdown time left after a selection that completed a triple of [matchedType]. */
    fun slowdownAfterMatch(
        remainingMillis: Long,
        matchedType: TileType?,
    ): Long = if (isRecoveryMatch(matchedType)) config.musicSlowdownMillis else remainingMillis

    /**
     * Stress points that [elapsedMillis] of play adds while [remainingMillis] of slowdown is
     * left. Only the slowed part of the interval is scaled, so a tick that crosses the end of
     * the effect is split exactly.
     */
    fun accumulationFor(
        elapsedMillis: Long,
        remainingMillis: Long,
    ): Double {
        val slowed = slowedMillis(elapsedMillis, remainingMillis)
        return stressManager.accumulationFor(elapsedMillis - slowed) +
            stressManager.accumulationFor(slowed) * config.musicSlowdownMultiplier
    }

    /** Slowdown time left once [elapsedMillis] of play has passed. */
    fun remainingAfter(
        elapsedMillis: Long,
        remainingMillis: Long,
    ): Long = remainingMillis - slowedMillis(elapsedMillis, remainingMillis)

    private fun slowedMillis(
        elapsedMillis: Long,
        remainingMillis: Long,
    ): Long {
        require(elapsedMillis >= 0L) {
            "Elapsed time must be non-negative."
        }
        return minOf(elapsedMillis, remainingMillis.coerceAtLeast(0L))
    }

    companion object {
        /** The tile type whose completed triple triggers the Music effect. */
        val RECOVERY_TYPE = TileType.MUSIC
    }
}
