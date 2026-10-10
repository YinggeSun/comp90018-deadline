package com.comp90018.deadline.domain.game.shuffle

import com.comp90018.deadline.domain.game.model.Board
import kotlin.random.Random

/**
 * Pure game-domain shuffle logic for issue #21.
 *
 * For the real game board, tile identity and geometry stay fixed while tile types are
 * redistributed between the existing board slots. This is important because overlap
 * availability is keyed by tile id/position and must remain valid after a shuffle.
 * Tiles already moved into the Task Tray are not part of [Board], so they are never
 * touched by this class.
 */
class BoardShuffler(
    private val random: Random = Random.Default,
) {
    /**
     * Shuffles only the matchable contents of the remaining board.
     *
     * - tile id is preserved
     * - tile position/layer is preserved
     * - no tile is added or removed
     * - only tile type assignments may change
     */
    fun shuffle(board: Board): Board {
        if (board.tiles.size < 2) return board

        val shuffledTypes = board.tiles.map { it.type }.shuffled(random)
        return Board(
            board.tiles.mapIndexed { index, tile ->
                tile.copy(type = shuffledTypes[index])
            },
        )
    }

    /**
     * Small generic helper retained for unit tests/debug tooling.
     * Slot identity stays fixed while values are shuffled.
     */
    fun <T> shuffle(slots: List<ShuffleSlot<T>>): List<ShuffleSlot<T>> {
        if (slots.size < 2) return slots.toList()

        val shuffledValues = slots.map { it.value }.shuffled(random)
        return slots.mapIndexed { index, slot ->
            slot.copy(value = shuffledValues[index])
        }
    }
}

/** Generic fixed-slot representation used by tests/debug tooling. */
data class ShuffleSlot<T>(
    val slotId: String,
    val value: T,
)
