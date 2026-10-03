package com.comp90018.deadline.domain.game.stress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StressManagerTest {
    @Test
    fun accumulationAppliesTheWeeksRate() {
        val manager = StressManager(StressConfig(baseRate = 2, rateGrowthPerWeek = 3))

        assertEquals(2, manager.accumulate(current = 0, week = 1))
        assertEquals(15, manager.accumulate(current = 10, week = 2))
        assertEquals(24, manager.accumulate(current = 10, week = 5))
    }

    @Test
    fun repeatedAccumulationSaturatesAtTheMaximum() {
        val manager = StressManager(StressConfig(maximum = 10, baseRate = 4, rateGrowthPerWeek = 0))
        var stress = 0

        repeat(5) { stress = manager.accumulate(stress, week = 1) }

        assertEquals(10, stress)
    }

    @Test
    fun increaseSaturatesInsteadOfOverflowing() {
        val manager = StressManager(StressConfig(maximum = 100))

        assertEquals(100, manager.increaseBy(current = 99, amount = 2))
        assertEquals(100, manager.increaseBy(current = 0, amount = Int.MAX_VALUE))
        assertEquals(100, manager.increaseBy(current = Int.MAX_VALUE, amount = Int.MAX_VALUE))
    }

    @Test
    fun decreaseStopsAtZeroInsteadOfUnderflowing() {
        val manager = StressManager()

        assertEquals(0, manager.decreaseBy(current = 3, amount = 10))
        assertEquals(0, manager.decreaseBy(current = 0, amount = Int.MAX_VALUE))
        assertEquals(0, manager.decreaseBy(current = Int.MIN_VALUE, amount = 1))
    }

    @Test
    fun zeroAmountLeavesStressUnchanged() {
        val manager = StressManager()

        assertEquals(42, manager.increaseBy(current = 42, amount = 0))
        assertEquals(42, manager.decreaseBy(current = 42, amount = 0))
    }

    @Test
    fun clampRepairsOutOfRangeStoredState() {
        val manager = StressManager(StressConfig(maximum = 60))

        assertEquals(0, manager.clamp(-5))
        assertEquals(60, manager.clamp(120))
        assertEquals(30, manager.clamp(30))
    }

    @Test
    fun highStressStartsAtTheThresholdItself() {
        val manager = StressManager(StressConfig(maximum = 100, highStressThreshold = 75))

        assertFalse(manager.isHighStress(74))
        assertTrue(manager.isHighStress(75))
        assertTrue(manager.isHighStress(100))
    }

    @Test
    fun highStressTreatsOutOfRangeStateAsClamped() {
        val manager = StressManager(StressConfig(maximum = 100, highStressThreshold = 75))

        assertFalse(manager.isHighStress(-10))
        assertTrue(manager.isHighStress(500))
    }

    @Test
    fun recoveryCanClearHighStress() {
        val manager = StressManager(StressConfig(maximum = 100, highStressThreshold = 75))
        val recovered = manager.decreaseBy(current = 80, amount = 20)

        assertEquals(60, recovered)
        assertFalse(manager.isHighStress(recovered))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeIncrease() {
        StressManager().increaseBy(current = 10, amount = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDecrease() {
        StressManager().decreaseBy(current = 10, amount = -1)
    }
}
