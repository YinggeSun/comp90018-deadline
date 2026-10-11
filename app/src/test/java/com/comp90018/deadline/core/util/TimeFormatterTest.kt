package com.comp90018.deadline.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {
    @Test
    fun formatsWholeSecondsAsMinutesAndSeconds() {
        assertEquals("00:00", TimeFormatter.formatSeconds(0))
        assertEquals("01:05", TimeFormatter.formatSeconds(65))
    }

    @Test
    fun formatsHundredthsAndDropsThousandths() {
        assertEquals("00:00.00", TimeFormatter.formatHundredths(0))
        assertEquals("01:05.37", TimeFormatter.formatHundredths(65_379))
        assertEquals("00:09.05", TimeFormatter.formatHundredths(9_050))
        assertEquals("10:00.00", TimeFormatter.formatHundredths(600_000))
    }
}
