package com.comp90018.deadline.domain.game.shuffle

import kotlin.random.Random

/**
 * Pure game-domain shuffle logic for issue #21.
 *
 * The board geometry is represented by [slotId]. Only [value] is shuffled.
 * Therefore positions/layers stay unchanged and no item is lost or duplicated.
 * The Task Tray is intentionally not part of this API, so it cannot be modified here.
 */
data class ShuffleSlot<T>(
    val slotId: String,
    val value: T,
)

class BoardShuffler(
    private val random: Random = Random.Default,
) {
    fun <T> shuffle(slots: List<ShuffleSlot<T>>): List<ShuffleSlot<T>> {
        if (slots.size < 2) return slots.toList()

        val shuffledValues = slots.map { it.value }.shuffled(random)
        return slots.mapIndexed { index, slot ->
            slot.copy(value = shuffledValues[index])
        }
    }
}
