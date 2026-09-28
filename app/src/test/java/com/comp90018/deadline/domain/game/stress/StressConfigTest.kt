package com.comp90018.deadline.domain.game.stress

import org.junit.Assert.assertEquals
import org.junit.Test

class StressConfigTest {
    @Test
    fun defaultsDescribeAHundredPointRangeWithAHighStressBand() {
        val config = StressConfig()

        assertEquals(100, config.maximum)
        assertEquals(75, config.highStressThreshold)
        assertEquals(0.25, config.degradationProbability, 0.0)
    }

    @Test
    fun rateGrowsLinearlyFromTheFirstWeek() {
        val config = StressConfig(baseRate = 2, rateGrowthPerWeek = 3)

        assertEquals(2, config.rateForWeek(1))
        assertEquals(5, config.rateForWeek(2))
        assertEquals(14, config.rateForWeek(5))
    }

    @Test
    fun flatRateIgnoresTheWeek() {
        val config = StressConfig(baseRate = 4, rateGrowthPerWeek = 0)

        assertEquals(4, config.rateForWeek(1))
        assertEquals(4, config.rateForWeek(12))
    }

    @Test
    fun oneStepNeverExceedsTheFullRangeEvenForAnExtremeWeek() {
        val config = StressConfig(maximum = 50, baseRate = 1, rateGrowthPerWeek = 9)

        assertEquals(50, config.rateForWeek(100))
        assertEquals(50, config.rateForWeek(Int.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroMaximum() {
        StressConfig(maximum = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsThresholdAboveMaximum() {
        StressConfig(maximum = 10, highStressThreshold = 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroThresholdBecauseAFreshGameWouldStartStressed() {
        StressConfig(highStressThreshold = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProbabilityAboveOne() {
        StressConfig(degradationProbability = 1.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeProbability() {
        StressConfig(degradationProbability = -0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeBaseRate() {
        StressConfig(baseRate = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeGrowth() {
        StressConfig(rateGrowthPerWeek = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveWeek() {
        StressConfig().rateForWeek(0)
    }

    @Test
    fun highStressBandDefaultsToTheTopQuarterOfWhateverRangeIsConfigured() {
        assertEquals(75, StressConfig(maximum = 100).highStressThreshold)
        assertEquals(45, StressConfig(maximum = 60).highStressThreshold)
        assertEquals(7, StressConfig(maximum = 10).highStressThreshold)
    }

    @Test
    fun smallestRangeStillLeavesAReachableHighStressThreshold() {
        assertEquals(1, StressConfig(maximum = 1).highStressThreshold)
    }

    @Test
    fun explicitThresholdOverridesTheDerivedDefault() {
        assertEquals(20, StressConfig(maximum = 100, highStressThreshold = 20).highStressThreshold)
    }
}
