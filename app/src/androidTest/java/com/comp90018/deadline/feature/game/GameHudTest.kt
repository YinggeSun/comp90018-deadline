package com.comp90018.deadline.feature.game

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.STRESS_INDICATOR_TAG
import com.comp90018.deadline.feature.game.components.trayTileTestTag
import com.comp90018.deadline.feature.game.components.tileTestTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameHudTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun trayTile(id: String, type: TileType) =
        TileUiModel(id, type, row = 0, column = 0, layer = 0, isSelectable = false)

    private val baseState = GameUiState(
        levelName = "Test Level",
        boardTiles = listOf(TileUiModel("b1", TileType.BOOK, 0, 0, 0, isSelectable = true)),
        boardRows = 2,
        boardColumns = 2,
        trayCapacity = 7
    )

    private fun setContent(state: GameUiState, elapsedSeconds: Long = 0, onEvent: (GameUiEvent) -> Unit = {}) {
        composeRule.setContent {
            DeadlineTheme {
                GameContent(uiState = state, elapsedSeconds = elapsedSeconds, onEvent = onEvent, onBack = {})
            }
        }
    }

    @Test
    fun timerShowsMinutesAndSeconds() {
        setContent(baseState, elapsedSeconds = 125)

        composeRule.onNodeWithContentDescription("Time 02:05").assertIsDisplayed()
    }

    @Test
    fun trayShowsTilesAndCount() {
        val tray = listOf(trayTile("t1", TileType.BOOK), trayTile("t2", TileType.COFFEE))
        setContent(baseState.copy(trayTiles = tray))

        composeRule.onNodeWithText("2/7").assertIsDisplayed()
        composeRule.onNodeWithTag(trayTileTestTag("t1")).assertIsDisplayed()
        composeRule.onNodeWithTag(trayTileTestTag("t2")).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Task Tray, 2 of 7 slots used").assertExists()
    }

    @Test
    fun highStressShowsWarning() {
        setContent(baseState.copy(stress = 90, isHighStress = true))

        composeRule.onNodeWithTag(STRESS_INDICATOR_TAG)
            .assertContentDescriptionEquals("Stress 90 of 100")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "High stress!"))
    }

    @Test
    fun maxStressShowsUnreliableInputWarning() {
        setContent(baseState.copy(stress = 100, isHighStress = true, isMaxStress = true))

        composeRule.onNodeWithTag(STRESS_INDICATOR_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Maximum stress!"))
        composeRule.onNodeWithTag(MAX_STRESS_WARNING_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Maximum stress — some tile selections may be incorrect.").assertIsDisplayed()
    }

    @Test
    fun belowMaxStressHasNoUnreliableInputWarning() {
        setContent(baseState.copy(stress = 90, isHighStress = true))

        composeRule.onNodeWithTag(MAX_STRESS_WARNING_TAG).assertDoesNotExist()
    }

    @Test
    fun normalStressHasNoWarning() {
        setContent(baseState.copy(stress = 20, isHighStress = false))

        composeRule.onNodeWithTag(STRESS_INDICATOR_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun undoFollowsCanUndoAndSendsEvent() {
        val events = mutableListOf<GameUiEvent>()
        setContent(baseState.copy(canUndo = false), onEvent = { events += it })
        composeRule.onNodeWithText("Undo").assertIsNotEnabled()

        composeRule.onNodeWithText("Restart").performClick()

        assertEquals(listOf<GameUiEvent>(GameUiEvent.RestartClicked), events)
    }

    @Test
    fun enabledUndoSendsEvent() {
        val events = mutableListOf<GameUiEvent>()
        setContent(baseState.copy(canUndo = true), onEvent = { events += it })

        composeRule.onNodeWithText("Undo").assertIsEnabled().performClick()

        assertEquals(listOf<GameUiEvent>(GameUiEvent.UndoClicked), events)
    }

    @Test
    fun tappedTileMovesFromBoardToTray() {
        val level = FixedLevels.LEVEL_3
        val viewModel = GameViewModel(level.id)
        composeRule.setContent {
            DeadlineTheme {
                GameScreen(levelId = level.id, onGameFinished = {}, onBack = {}, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithTag(tileTestTag("level_3_laptop_1")).performClick()

        composeRule.onNodeWithTag(tileTestTag("level_3_laptop_1")).assertDoesNotExist()
        composeRule.onNodeWithTag(trayTileTestTag("level_3_laptop_1")).assertIsDisplayed()
        composeRule.onNodeWithText("1/7").assertIsDisplayed()
    }
}
