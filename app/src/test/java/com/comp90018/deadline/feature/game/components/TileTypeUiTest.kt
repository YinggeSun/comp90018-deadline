package com.comp90018.deadline.feature.game.components

import com.comp90018.deadline.domain.game.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator

class TileTypeUiTest {
    private fun characterCount(text: String): Int {
        val boundaries = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        while (boundaries.next() != BreakIterator.DONE) count++
        return count
    }

    @Test
    fun everyTileTypeHasItsOwnSymbol() {
        val symbols = TileType.entries.map { it.symbol }

        assertTrue(symbols.none { it.isBlank() })
        assertEquals("Each tile type needs a distinct symbol", symbols.size, symbols.toSet().size)
    }

    @Test
    fun everySymbolIsOneCharacter() {
        // A single emoji per tile keeps the icon readable at small sizes on every phone.
        TileType.entries.forEach { type ->
            assertEquals("${type.name} symbol", 1, characterCount(type.symbol))
        }
    }

    @Test
    fun everyTileTypeHasItsOwnSpokenName() {
        val labels = TileType.entries.map { it.labelRes }

        assertEquals("Each tile type needs a distinct content description", labels.size, labels.toSet().size)
    }
}
