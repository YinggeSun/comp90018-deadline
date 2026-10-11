package com.comp90018.deadline.feature.game

import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.domain.progress.CompletionRecorder
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Winning a level unlocks the next one, although each level spans two weeks. */
class LevelUnlockRecordingTest {
    private fun win(level: SemesterLevel): CompletionResult {
        // Any easy board will do; it plays as the requested level.
        val board = FixedLevels.LEVEL_1.copy(id = level.id, name = level.name, week = level.firstWeek)
        val recorded = mutableListOf<CompletionResult>()
        val viewModel =
            GameViewModel(board.id, findLevel = { board }, completionRecorder = CompletionRecorder { recorded += it })

        board.board.tiles.forEach { viewModel.onEvent(GameUiEvent.TileTapped(it.id)) }

        return recorded.single()
    }

    @Test
    fun winIsRecordedForTheLevelsLastWeek() {
        SemesterLevel.ALL.forEach { level ->
            assertEquals(level.lastWeek, win(level).week)
        }
    }

    @Test
    fun winningEachLevelUnlocksTheNextLevel() {
        var progress = PlayerProgress()
        SemesterLevel.ALL.zipWithNext().forEach { (current, next) ->
            assertTrue("${current.name} should be open", progress.isWeekUnlocked(current.firstWeek))
            progress = progress.withCompletion(win(current)).first
            assertTrue("${next.name} should open after ${current.name}", progress.isWeekUnlocked(next.firstWeek))
        }
    }
}
