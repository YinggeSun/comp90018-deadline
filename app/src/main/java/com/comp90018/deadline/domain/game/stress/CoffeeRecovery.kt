package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.model.TileType

/**
 * Coffee Recovery rules: clearing a Coffee triple removes stress.
 *
 * Coffee is an ordinary matchable [TileType], not a power-up with its own selection path, so
 * these rules never remove tiles or touch the board. The engine resolves the triple exactly
 * as it resolves any other type and then asks this class what the new stress value is, which
 * is what guarantees normal triple removal still happens when the matched type is Coffee.
 *
 * Like [StressManager], every operation takes the current value and returns the next one, so
 * no session state lives here. The recovery amount comes from the week and the configuration;
 * see [StressConfig.coffeeRecoveryForWeek].
 */
class CoffeeRecovery(private val config: StressConfig = StressConfig()) {
    private val stressManager = StressManager(config)

    /** Stress removed by a Coffee triple completed during [week]. */
    fun recoveryForWeek(week: Int): Int = config.coffeeRecoveryForWeek(week)

    /**
     * True when completing a triple of [matchedType] triggers recovery. A null type means the
     * selection completed no triple, which is how the engine reports a plain selection.
     */
    fun isRecoveryMatch(matchedType: TileType?): Boolean = matchedType == RECOVERY_TYPE

    /**
     * Stress after a selection that completed a triple of [matchedType], or after one that
     * completed none when [matchedType] is null.
     *
     * Only a Coffee triple changes the value; every other match leaves it as it is. The result
     * always lies within `0..`[StressConfig.maximum], so a recovery larger than the current
     * stress stops at zero rather than going negative, and an out-of-range [current] coming
     * from restored state is repaired instead of being propagated.
     *
     * [week] is validated for every call, not only for a Coffee triple, so a caller cannot
     * discover an invalid week only once the player happens to clear coffee.
     */
    fun applyMatch(
        current: Int,
        matchedType: TileType?,
        week: Int,
    ): Int {
        val recovery = recoveryForWeek(week)
        return stressManager.decreaseBy(
            current = current,
            amount = if (isRecoveryMatch(matchedType)) recovery else 0,
        )
    }

    companion object {
        /** The tile type whose completed triple restores stress. */
        val RECOVERY_TYPE = TileType.COFFEE
    }
}
