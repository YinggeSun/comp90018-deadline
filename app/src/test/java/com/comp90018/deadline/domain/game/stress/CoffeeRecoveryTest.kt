package com.comp90018.deadline.domain.game.stress

import com.comp90018.deadline.domain.game.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoffeeRecoveryTest {
    @Test
    fun coffeeIsTheOnlyRecoveryType() {
        val recovery = CoffeeRecovery()

        assertEquals(TileType.COFFEE, CoffeeRecovery.RECOVERY_TYPE)
        assertTrue(recovery.isRecoveryMatch(TileType.COFFEE))
        for (other in TileType.entries - TileType.COFFEE) {
            assertFalse("$other must not recover stress", recovery.isRecoveryMatch(other))
        }
    }

    @Test
    fun aSelectionThatCompletesNoTripleIsNotARecoveryMatch() {
        assertFalse(CoffeeRecovery().isRecoveryMatch(null))
    }

    @Test
    fun onlyACompletedCoffeeTripleLowersStress() {
        val recovery = CoffeeRecovery(config(recoveryBase = 20))

        assertEquals(30, recovery.applyMatch(current = 50, matchedType = TileType.COFFEE, week = 1))
        for (unchanged in TileType.entries - TileType.COFFEE) {
            assertEquals(50, recovery.applyMatch(current = 50, matchedType = unchanged, week = 1))
        }
        assertEquals(50, recovery.applyMatch(current = 50, matchedType = null, week = 1))
    }

    @Test
    fun recoveryShrinksAsTheSemesterProgresses() {
        val recovery = CoffeeRecovery(config(recoveryBase = 20, recoveryDecline = 2))

        assertEquals(20, recovery.recoveryForWeek(1))
        assertEquals(18, recovery.recoveryForWeek(2))
        assertEquals(2, recovery.recoveryForWeek(10))
        assertEquals(80, recovery.applyMatch(current = 100, matchedType = TileType.COFFEE, week = 1))
        assertEquals(98, recovery.applyMatch(current = 100, matchedType = TileType.COFFEE, week = 10))
    }

    @Test
    fun aLateSemesterCoffeeCanStopRecoveringAltogether() {
        val recovery = CoffeeRecovery(config(recoveryBase = 5, recoveryDecline = 1))

        assertEquals(0, recovery.recoveryForWeek(6))
        assertEquals(0, recovery.recoveryForWeek(Int.MAX_VALUE))
        assertEquals(40, recovery.applyMatch(current = 40, matchedType = TileType.COFFEE, week = 6))
    }

    @Test
    fun aFlatConfigIgnoresTheWeek() {
        val recovery = CoffeeRecovery(config(recoveryBase = 15, recoveryDecline = 0))

        assertEquals(15, recovery.recoveryForWeek(1))
        assertEquals(15, recovery.recoveryForWeek(12))
        assertEquals(25, recovery.applyMatch(current = 40, matchedType = TileType.COFFEE, week = 12))
    }

    @Test
    fun recoveryStopsAtZeroInsteadOfGoingNegative() {
        val recovery = CoffeeRecovery(config(recoveryBase = 30))

        assertEquals(0, recovery.applyMatch(current = 30, matchedType = TileType.COFFEE, week = 1))
        assertEquals(0, recovery.applyMatch(current = 5, matchedType = TileType.COFFEE, week = 1))
        assertEquals(0, recovery.applyMatch(current = 0, matchedType = TileType.COFFEE, week = 1))
    }

    @Test
    fun recoveryNeverRestoresMoreThanTheFullRange() {
        val recovery = CoffeeRecovery(config(maximum = 50, recoveryBase = Int.MAX_VALUE))

        assertEquals(50, recovery.recoveryForWeek(1))
        assertEquals(0, recovery.applyMatch(current = 50, matchedType = TileType.COFFEE, week = 1))
    }

    @Test
    fun outOfRangeStoredStressIsRepairedRatherThanPropagated() {
        val recovery = CoffeeRecovery(config(maximum = 60, recoveryBase = 10))

        assertEquals(55, recovery.applyMatch(current = 65, matchedType = TileType.COFFEE, week = 1))
        assertEquals(60, recovery.applyMatch(current = 65, matchedType = TileType.BOOK, week = 1))
        assertEquals(60, recovery.applyMatch(current = Int.MAX_VALUE, matchedType = TileType.COFFEE, week = 1))
        assertEquals(0, recovery.applyMatch(current = Int.MIN_VALUE, matchedType = TileType.COFFEE, week = 1))
    }

    @Test
    fun coffeeCanClearHighStress() {
        val config = config(maximum = 100, recoveryBase = 20)
        val recovery = CoffeeRecovery(config)
        val stressManager = StressManager(config)

        assertTrue(stressManager.isHighStress(80))
        val recovered = recovery.applyMatch(current = 80, matchedType = TileType.COFFEE, week = 1)

        assertEquals(60, recovered)
        assertFalse(stressManager.isHighStress(recovered))
    }

    @Test
    fun coffeeAtTheThresholdLeavesHighStressWhenRecoveryIsTooSmall() {
        val config = config(maximum = 100, recoveryBase = 1)
        val recovery = CoffeeRecovery(config)
        val stressManager = StressManager(config)

        val recovered = recovery.applyMatch(current = 90, matchedType = TileType.COFFEE, week = 1)

        assertEquals(89, recovered)
        assertTrue(stressManager.isHighStress(recovered))
    }

    @Test
    fun repeatedCoffeeMatchesSaturateAtZero() {
        val recovery = CoffeeRecovery(config(recoveryBase = 40))
        var stress = 100

        repeat(4) { stress = recovery.applyMatch(stress, TileType.COFFEE, week = 1) }

        assertEquals(0, stress)
    }

    @Test
    fun defaultsRecoverAMeaningfulShareOfTheRange() {
        val recovery = CoffeeRecovery()

        assertEquals(StressConfig.DEFAULT_COFFEE_RECOVERY_BASE, recovery.recoveryForWeek(1))
        assertEquals(80, recovery.applyMatch(current = 100, matchedType = TileType.COFFEE, week = 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveWeek() {
        CoffeeRecovery().recoveryForWeek(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveWeekEvenWithoutACoffeeMatch() {
        CoffeeRecovery().applyMatch(current = 10, matchedType = TileType.BOOK, week = 0)
    }

    private fun config(
        maximum: Int = 100,
        recoveryBase: Int = StressConfig.DEFAULT_COFFEE_RECOVERY_BASE,
        recoveryDecline: Int = 0,
    ) = StressConfig(
        maximum = maximum,
        coffeeRecoveryBase = recoveryBase,
        coffeeRecoveryDeclinePerWeek = recoveryDecline,
    )
}
