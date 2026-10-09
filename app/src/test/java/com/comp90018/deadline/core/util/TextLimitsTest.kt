package com.comp90018.deadline.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLimitsTest {
    @Test
    fun shortTextIsUnchanged() {
        assertEquals("Hao", "Hao".takeWholeCharacters(20))
    }

    @Test
    fun plainTextIsCutAtTheLimit() {
        assertEquals("abcde", "abcdefgh".takeWholeCharacters(5))
    }

    @Test
    fun surrogatePairIsDroppedRatherThanSplit() {
        // "ab" + 😀 is four UTF-16 units; three would split the emoji.
        assertEquals("ab", "ab😀".takeWholeCharacters(3))
        assertEquals("ab😀", "ab😀c".takeWholeCharacters(4))
    }

    @Test
    fun letterWithCombiningAccentStaysTogether() {
        // "e" followed by a combining acute accent is one character in two units.
        assertEquals("ab", "abé".takeWholeCharacters(3))
        assertEquals("abé", "abéx".takeWholeCharacters(4))
    }

    @Test
    fun zeroLimitGivesEmptyText() {
        assertEquals("", "abc".takeWholeCharacters(0))
    }
}
