package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.GameBoard
import com.comp90018.deadline.feature.game.components.tileTestTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val selectable = TileUiModel("top", TileType.LAPTOP, row = 0, column = 1, layer = 1, isSelectable = true)
    private val covered = TileUiModel("bottom", TileType.BOOK, row = 0, column = 0, layer = 0, isSelectable = false)

    private fun setBoard(onTileClick: (String) -> Unit) {
        composeRule.setContent {
            DeadlineTheme {
                GameBoard(
                    tiles = listOf(covered, selectable),
                    rows = 2,
                    columns = 3,
                    onTileClick = onTileClick,
                    modifier = Modifier.size(300.dp)
                )
            }
        }
    }

    @Test
    fun selectableTileReportsTap() {
        val tapped = mutableListOf<String>()
        setBoard { tapped += it }

        composeRule.onNodeWithTag(tileTestTag("top")).assertIsEnabled().performClick()

        assertEquals(listOf("top"), tapped)
    }

    @Test
    fun coveredTileIsDisabled() {
        val tapped = mutableListOf<String>()
        setBoard { tapped += it }

        composeRule.onNodeWithTag(tileTestTag("bottom")).assertIsNotEnabled()

        assertFalse(tapped.contains("bottom"))
    }

    @Test
    fun screenRendersEveryBoardTileFromViewModel() {
        val level = FixedLevels.LEVEL_3
        val viewModel = GameViewModel(level.id)
        composeRule.setContent {
            DeadlineTheme {
                GameScreen(levelId = level.id, onGameFinished = {}, onBack = {}, viewModel = viewModel)
            }
        }

        level.board.tiles.forEach { composeRule.onNodeWithTag(tileTestTag(it.id)).assertExists() }

        composeRule.onNodeWithTag(tileTestTag("level_3_laptop_1")).performClick()
        composeRule.onNodeWithTag(tileTestTag("level_3_laptop_1")).assertDoesNotExist()
    }
}
