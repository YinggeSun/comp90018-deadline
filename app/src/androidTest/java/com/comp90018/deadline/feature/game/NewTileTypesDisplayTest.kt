package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.feature.game.components.GameBoard
import com.comp90018.deadline.feature.game.components.TaskTray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The tile types added for #100 show on the board and in the tray with their own names. */
@RunWith(AndroidJUnit4::class)
class NewTileTypesDisplayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val names =
        mapOf(
            TileType.MUSIC to "Music tile",
            TileType.EXAM to "Exam tile",
            TileType.PRESENTATION to "Presentation tile",
            TileType.LECTURE_SLIDE to "Lecture slide tile",
        )

    private fun tile(
        type: TileType,
        index: Int,
    ) = TileUiModel(type.name, type, row = 0, column = index * 2, layer = 0, isSelectable = true)

    @Test
    fun newTilesShowOnTheBoard() {
        val tiles = names.keys.mapIndexed { index, type -> tile(type, index) }
        composeRule.setContent {
            DeadlineTheme {
                GameBoard(tiles = tiles, rows = 2, columns = 8, onTileClick = {}, modifier = Modifier.size(400.dp))
            }
        }

        names.values.forEach { composeRule.onNodeWithContentDescription(it).assertIsDisplayed() }
    }

    @Test
    fun newTilesShowInTheTray() {
        val tiles = names.keys.mapIndexed { index, type -> tile(type, index) }
        composeRule.setContent {
            DeadlineTheme { TaskTray(tiles = tiles, capacity = 7) }
        }

        names.values.forEach { composeRule.onNodeWithContentDescription(it).assertIsDisplayed() }
    }
}
