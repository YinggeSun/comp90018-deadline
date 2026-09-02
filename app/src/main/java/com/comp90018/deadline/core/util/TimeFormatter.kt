package com.comp90018.deadline.core.util

object TimeFormatter {
    fun formatSeconds(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
}

