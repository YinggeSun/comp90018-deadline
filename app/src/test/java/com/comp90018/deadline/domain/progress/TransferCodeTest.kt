package com.comp90018.deadline.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCodeTest {
    @Test
    fun generatedCodesUseOnlyTheAlphabetAndTheRightLength() {
        repeat(1_000) {
            val code = TransferCode.generate()
            assertEquals(TransferCode.LENGTH, code.length)
            assertTrue(code.all { it in TransferCode.ALPHABET })
        }
    }

    @Test
    fun generatedCodesDoNotRepeat() {
        assertEquals(10_000, List(10_000) { TransferCode.generate() }.toSet().size)
    }

    @Test
    fun alphabetHasNoLookAlikes() {
        assertEquals(32, TransferCode.ALPHABET.toSet().size)
        assertTrue("0O1I".none { it in TransferCode.ALPHABET })
    }

    @Test
    fun typedCodesAreNormalized() {
        assertEquals("K7QM3XPD", TransferCode.normalize("k7qm-3xpd"))
        assertEquals("K7QM3XPD", TransferCode.normalize(" K7QM 3XPD "))
    }

    @Test
    fun impossibleCodesAreRejected() {
        assertNull(TransferCode.normalize("K7QM-3XP"))
        assertNull(TransferCode.normalize("K7QM-3XPDA"))
        assertNull(TransferCode.normalize("K7QM-3XP0"))
        assertNull(TransferCode.normalize(""))
    }

    @Test
    fun codesAreShownInTwoGroups() {
        assertEquals("K7QM-3XPD", TransferCode.format("K7QM3XPD"))
    }
}
