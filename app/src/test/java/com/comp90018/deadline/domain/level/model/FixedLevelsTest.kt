package com.comp90018.deadline.domain.level.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedLevelsTest {

    @Test
    fun sampleLevel_hasExpectedConfiguration() {
        val level = FixedLevels.SAMPLE_LEVEL

        assertEquals("sample_level", level.id)
        assertEquals("Sample Level", level.name)
        assertEquals(3, level.board.tiles.size)

        assertEquals(2, level.config.layout.rows)
        assertEquals(2, level.config.layout.columns)
        assertEquals(3, level.config.tileCount)
        assertEquals(0, level.config.maxLayer)
    }
}
