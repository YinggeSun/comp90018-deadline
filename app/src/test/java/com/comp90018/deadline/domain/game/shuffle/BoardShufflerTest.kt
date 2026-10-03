package com.comp90018.deadline.domain.game.shuffle

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class BoardShufflerTest {
    @Test
    fun shuffle_preservesSlotsAndValues() {
        val before = listOf(
            ShuffleSlot("slot-A", "book"),
            ShuffleSlot("slot-B", "code"),
            ShuffleSlot("slot-C", "coffee"),
            ShuffleSlot("slot-D", "quiz"),
        )

        val after = BoardShuffler(Random(1234)).shuffle(before)

        assertEquals(before.map { it.slotId }, after.map { it.slotId })
        assertEquals(before.map { it.value }.sorted(), after.map { it.value }.sorted())
        assertEquals(before.size, after.size)
    }
}
