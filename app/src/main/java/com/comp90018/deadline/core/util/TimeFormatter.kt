package com.comp90018.deadline.core.util

object TimeFormatter {
    fun formatSeconds(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    /**
     * Minutes, seconds and hundredths, e.g. "01:05.37". Used where players are compared,
     * so times within the same second can still be told apart.
     */
    fun formatHundredths(millis: Long): String {
        val seconds = millis / 1_000
        return "%s.%02d".format(formatSeconds(seconds), millis % 1_000 / 10)
    }
}
