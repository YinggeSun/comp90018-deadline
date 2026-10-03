package com.comp90018.deadline.domain.level.solver

import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.level.model.Level

/** A limit result is inconclusive and must never be treated as proof of no solution. */
sealed class SolvabilityResult {
    data class Solvable(val moves: List<String>) : SolvabilityResult()
    object Unsolvable : SolvabilityResult()
    object SearchLimitReached : SolvabilityResult()
    data class InvalidBoard(val reason: String) : SolvabilityResult()
}

/**
 * Solvable means at least one sequence of legal selections clears both board and
 * seven-slot tray, resolving triples before checking capacity, without power-ups.
 * Validates a fresh level with an empty tray, not an in-progress game.
 * Pure JVM logic; callers should run expensive searches off the UI thread.
 */
class SolvabilityValidator(
    private val solver: BacktrackingSolver = BacktrackingSolver()
) {
    fun validate(level: Level): SolvabilityResult {
        if (level.board.tiles.size != level.config.tileCount) {
            return SolvabilityResult.InvalidBoard("Board size does not match tileCount.")
        }
        if (level.board.tiles.any { it.position.layer > level.config.maxLayer }) {
            return SolvabilityResult.InvalidBoard("Tile exceeds maxLayer.")
        }
        return validate(level.board)
    }

    fun validate(board: Board): SolvabilityResult = solver.solve(board)
}
