package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.domain.level.solver.SolvabilityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedLevelSourceTest {
    private val source = GeneratedLevelSource(dispatcher = Dispatchers.Unconfined)

    @Test
    fun generatesTheRequestedLevel() =
        runBlocking {
            val requested = SemesterLevel(3)

            val level = source.load(requested.id)!!

            assertEquals(requested.id, level.id)
            assertEquals(requested.name, level.name)
            assertEquals(requested.firstWeek, level.week)
            assertEquals(requested.tileCount, level.board.tiles.size)
        }

    @Test
    fun unknownLevelGivesNothing() =
        runBlocking {
            assertNull(source.load("level_7"))
            assertNull(source.load("sample_level"))
        }

    @Test
    fun startingALevelAgainCanGiveADifferentBoard() =
        runBlocking {
            val id = SemesterLevel(2).id
            val first = source.load(id)!!.board

            // Each start uses a new random seed; a few tries are enough to see a different board.
            val others = List(5) { source.load(id)!!.board }

            assertTrue(others.any { it != first })
        }

    @Test
    fun fallbackKeepsTheRequestedLevelsIdentity() =
        runBlocking {
            val requested = SemesterLevel(5)
            val failingGenerator =
                ValidatedLevelGenerator(
                    seed = 1L,
                    maxAttempts = 1,
                    createCandidate = { _, _, _, _ -> throw IllegalArgumentException("cannot place tiles") },
                    validateCandidate = { SolvabilityResult.Unsolvable },
                )
            val fallbackSource = GeneratedLevelSource(newGenerator = { failingGenerator }, dispatcher = Dispatchers.Unconfined)

            val level = fallbackSource.load(requested.id)!!

            assertEquals(requested.id, level.id)
            assertEquals(requested.name, level.name)
            assertEquals(requested.firstWeek, level.week)
            assertNotEquals(0, level.board.tiles.size)
        }
}
