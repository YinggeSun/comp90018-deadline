package com.comp90018.deadline.data.config

import com.comp90018.deadline.domain.config.GameBalance
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.sensor.shake.ShakeConfig
import com.comp90018.deadline.sensor.tilt.TiltConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBalanceTest {
    @Test
    fun noValuesGivesTheCompiledDefaults() {
        assertEquals(RemoteBalanceResult(GameBalance(), emptySet()), RemoteBalance.read(emptyMap()))
    }

    @Test
    fun theDefaultsReadBackUnchanged() {
        assertEquals(RemoteBalanceResult(GameBalance(), emptySet()), RemoteBalance.read(RemoteBalance.defaults()))
    }

    @Test
    fun everyKeyHasADefault() {
        assertEquals(RemoteBalance.KEYS, RemoteBalance.defaults().keys)
    }

    @Test
    fun validValuesAreApplied() {
        val result =
            RemoteBalance.read(
                mapOf(
                    RemoteBalance.STRESS_ACCUMULATION_PERCENT_PER_SECOND to "2.5",
                    RemoteBalance.STRESS_HIGH_THRESHOLD to "60",
                    RemoteBalance.COFFEE_RECOVERY_BASE to "30",
                    RemoteBalance.COFFEE_RECOVERY_DECLINE_PER_WEEK to "2",
                    RemoteBalance.SHAKE_THRESHOLD_G to "2.1",
                    RemoteBalance.SHAKE_COOLDOWN_MILLIS to "1500",
                    RemoteBalance.TILT_DEADZONE_DEGREES to "10",
                    RemoteBalance.TILT_FULL_PEEK_DEGREES to "40",
                ),
            )

        assertEquals(emptySet<String>(), result.rejectedKeys)
        assertEquals(2.5, result.balance.stress.accumulationPercentPerSecond, 0.0)
        assertEquals(60, result.balance.stress.highStressThreshold)
        assertEquals(28, result.balance.stress.coffeeRecoveryForWeek(2))
        assertEquals(2.1f, result.balance.shake.thresholdG)
        assertEquals(1_500L, result.balance.shake.cooldownMillis)
        assertEquals(TiltConfig(deadzoneDegrees = 10f, fullPeekDegrees = 40f), result.balance.tilt)
    }

    @Test
    fun malformedOrOutOfRangeValuesKeepTheirDefaultOnly() {
        val result =
            RemoteBalance.read(
                mapOf(
                    RemoteBalance.STRESS_DEGRADATION_PROBABILITY to "1.5",
                    RemoteBalance.STRESS_ACCUMULATION_PERCENT_PER_SECOND to "fast",
                    RemoteBalance.SHAKE_REQUIRED_HITS to "0",
                    RemoteBalance.SHAKE_THRESHOLD_G to "NaN",
                    RemoteBalance.TILT_SMOOTHING to "-1",
                    RemoteBalance.COFFEE_RECOVERY_BASE to "25",
                ),
            )

        assertEquals(
            setOf(
                RemoteBalance.STRESS_DEGRADATION_PROBABILITY,
                RemoteBalance.STRESS_ACCUMULATION_PERCENT_PER_SECOND,
                RemoteBalance.SHAKE_REQUIRED_HITS,
                RemoteBalance.SHAKE_THRESHOLD_G,
                RemoteBalance.TILT_SMOOTHING,
            ),
            result.rejectedKeys,
        )
        assertEquals(StressConfig(coffeeRecoveryBase = 25), result.balance.stress)
        assertEquals(ShakeConfig(), result.balance.shake)
        assertEquals(TiltConfig(), result.balance.tilt)
    }

    @Test
    fun valuesThatClashKeepTheirWholeGroupsDefaults() {
        val result =
            RemoteBalance.read(
                mapOf(
                    RemoteBalance.STRESS_MAXIMUM to "50",
                    RemoteBalance.STRESS_HIGH_THRESHOLD to "80",
                    RemoteBalance.TILT_DEADZONE_DEGREES to "30",
                    RemoteBalance.TILT_FULL_PEEK_DEGREES to "20",
                    RemoteBalance.SHAKE_COOLDOWN_MILLIS to "1200",
                ),
            )

        assertEquals(StressConfig(), result.balance.stress)
        assertEquals(TiltConfig(), result.balance.tilt)
        assertEquals(1_200L, result.balance.shake.cooldownMillis)
        assertTrue(RemoteBalance.STRESS_HIGH_THRESHOLD in result.rejectedKeys)
        assertTrue(RemoteBalance.TILT_FULL_PEEK_DEGREES in result.rejectedKeys)
    }

    @Test
    fun surroundingSpacesAreIgnoredAndUnknownKeysAreHarmless() {
        val result = RemoteBalance.read(mapOf(RemoteBalance.SHAKE_REQUIRED_HITS to " 3 ", "some_future_key" to "x"))

        assertEquals(3, result.balance.shake.requiredHits)
        assertEquals(emptySet<String>(), result.rejectedKeys)
    }

    @Test
    fun theConsoleTemplateMatchesTheAppsKeysAndDefaults() {
        // Unit tests run from the app module, so the template is one directory up.
        val template = java.io.File("../remoteconfig.template.json").readText()
        val defaults =
            Regex(""""(\w+)":\s*\{\s*"defaultValue":\s*\{\s*"value":\s*"([^"]+)"""")
                .findAll(template)
                .associate { it.groupValues[1] to it.groupValues[2] }

        assertEquals(RemoteBalance.KEYS, defaults.keys)
        RemoteBalance.defaults().forEach { (key, value) ->
            assertEquals(key, value.toDouble(), defaults.getValue(key).toDouble(), 1e-6)
        }
    }
}
