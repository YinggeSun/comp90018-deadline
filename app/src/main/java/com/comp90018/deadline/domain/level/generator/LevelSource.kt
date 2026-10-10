package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.SemesterLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Supplies the board for a level when it starts. */
fun interface LevelSource {
    /** The level with ID [levelId] and a board to play, or null if there is no such level. */
    suspend fun load(levelId: String): Level?
}

/**
 * Generates a new, solvable board each time a level starts, using a fresh random seed so
 * replays differ. Generation and solvability checking run on [dispatcher], off the main thread.
 *
 * If the generator falls back to its verified fixed level, the result keeps the requested
 * level's ID, name and week, so progress is still recorded against the level the player chose.
 */
class GeneratedLevelSource(
    private val newGenerator: () -> ValidatedLevelGenerator = { ValidatedLevelGenerator() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LevelSource {
    override suspend fun load(levelId: String): Level? {
        val requested = SemesterLevel.fromId(levelId) ?: return null
        return withContext(dispatcher) {
            val result = newGenerator().generateForLevel(requested.number)
            if (result.usedFallback) {
                result.level.copy(id = requested.id, name = requested.name, week = requested.firstWeek)
            } else {
                result.level
            }
        }
    }
}
