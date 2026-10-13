package com.comp90018.deadline.feature.game

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GameBoardCallbackTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun unchangedBoardUsesLatestHandlerAfterHudUpdates() {
        val generation = mutableIntStateOf(0)
        val calls = mutableListOf<Int>()
        val board = listOf(TileUiModel("one", TileType.COFFEE, 0, 0, 0, true))
        rule.setContent {
            val capturedGeneration = generation.intValue
            DeadlineTheme {
                GameContent(
                    GameUiState(boardTiles = board, boardRows = 1, boardColumns = 1, trayCapacity = 7, stress = capturedGeneration),
                    elapsedSeconds = capturedGeneration.toLong(),
                    onEvent = { calls += capturedGeneration },
                    onBack = {},
                )
            }
        }
        val oldClick = checkNotNull(rule.onNodeWithTag("tile_one").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        rule.runOnIdle { generation.intValue = 1 }
        rule.waitForIdle()
        rule.runOnUiThread { oldClick() }
        assertEquals(listOf(1), calls)
    }
}
