package com.comp90018.deadline.feature.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.level.generator.ValidatedLevelGenerator
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.settings.PlayerSettings
import com.comp90018.deadline.feature.game.components.GAME_BOARD_TAG
import com.comp90018.deadline.feature.game.components.GameBoard
import com.comp90018.deadline.feature.game.components.MaxTileSize
import com.comp90018.deadline.feature.game.components.MinTileSize
import com.comp90018.deadline.feature.game.components.tileTestTag
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tile size (#96): tiles are drawn between [MinTileSize] and [MaxTileSize], keep a 48dp
 * touch target, and stay inside the board, for fixed and generated boards on small,
 * normal and large screens.
 */
@RunWith(AndroidJUnit4::class)
class GameBoardSizingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val toleranceDp = 0.5f
    private val minTouchTarget = 48.dp
    private val allowedTileSize = (MinTileSize.value - toleranceDp)..(MaxTileSize.value + toleranceDp)

    private val screens =
        listOf(
            DpSize(320.dp, 568.dp), // small phone
            DpSize(411.dp, 891.dp), // typical phone
            DpSize(600.dp, 960.dp), // large phone or small tablet
        )

    private fun generated(levelNumber: Int): Level = ValidatedLevelGenerator(seed = 42L).generateForLevel(levelNumber).level

    private val levels by lazy { listOf(FixedLevels.LEVEL_3, generated(1), generated(6)) }

    /**
     * Motion controls off, so the emulator's real sensors cannot move tiles while sizes are
     * measured. Peeking is covered separately below.
     */
    private fun stillViewModel(level: Level) =
        GameViewModel(
            level.id,
            findLevel = { level },
            settings = flowOf(PlayerSettings(shakeToShuffleEnabled = false, tiltToPeekEnabled = false)),
        )

    private fun Rect.widthDp(density: Float): Dp = (width / density).dp

    private fun Rect.heightDp(density: Float): Dp = (height / density).dp

    private fun SemanticsNode.contains(other: SemanticsNode): Boolean {
        val outer = boundsInRoot
        val inner = other.boundsInRoot
        return inner.left >= outer.left - 1 && inner.top >= outer.top - 1 &&
            inner.right <= outer.right + 1 && inner.bottom <= outer.bottom + 1
    }

    @Test
    fun tilesStayWithinSizeLimitsAndInsideTheBoardOnEveryScreen() {
        var screen by mutableStateOf(screens.first())
        var level by mutableStateOf(levels.first())
        composeRule.setContent {
            DeadlineTheme {
                Box(modifier = Modifier.size(screen.width, screen.height)) {
                    GameScreen(
                        levelId = level.id,
                        onGameFinished = {},
                        onBack = {},
                        viewModel = remember(level) { stillViewModel(level) },
                    )
                }
            }
        }
        val density = composeRule.density.density

        for (s in screens) {
            for (l in levels) {
                composeRule.runOnIdle {
                    screen = s
                    level = l
                }
                composeRule.waitForIdle()
                val board = composeRule.onNodeWithTag(GAME_BOARD_TAG).fetchSemanticsNode()
                for (tile in l.board.tiles) {
                    val node = composeRule.onNodeWithTag(tileTestTag(tile.id)).fetchSemanticsNode()
                    val where = "${l.name} on ${s.width}x${s.height}, tile ${tile.id}"
                    val width = node.boundsInRoot.widthDp(density)
                    val height = node.boundsInRoot.heightDp(density)
                    assertTrue("$where is $width wide", width.value in allowedTileSize)
                    assertTrue("$where is $height tall", height.value in allowedTileSize)
                    assertTrue("$where touch area too small", node.touchBoundsInRoot.widthDp(density) >= minTouchTarget - toleranceDp.dp)
                    assertTrue("$where touch area too small", node.touchBoundsInRoot.heightDp(density) >= minTouchTarget - toleranceDp.dp)
                    assertTrue("$where is outside the board", board.contains(node))
                }
            }
        }
    }

    @Test
    fun smallBoardTilesAreCappedAtMaximumSize() {
        val level = generated(1)
        val viewModel = stillViewModel(level)
        composeRule.setContent {
            DeadlineTheme {
                Box(modifier = Modifier.size(600.dp, 960.dp)) {
                    GameScreen(levelId = level.id, onGameFinished = {}, onBack = {}, viewModel = viewModel)
                }
            }
        }
        val density = composeRule.density.density

        // A 600dp-wide screen would fit far larger tiles; they stop at the maximum instead.
        val width = composeRule.onNodeWithTag(tileTestTag(level.board.tiles.first().id)).fetchSemanticsNode().boundsInRoot.widthDp(density)
        assertTrue("tile is $width wide", (width.value - MaxTileSize.value) in -toleranceDp..toleranceDp)
    }

    @Test
    fun selectableTilesStayUsableWhilePeeking() {
        val level = generated(6)
        val state = GameViewModel(level.id, findLevel = { level }).uiState.value
        val tapped = mutableListOf<String>()
        composeRule.setContent {
            DeadlineTheme {
                GameBoard(
                    tiles = state.boardTiles,
                    rows = state.boardRows,
                    columns = state.boardColumns,
                    onTileClick = { tapped += it },
                    peekAmount = 1f,
                    modifier = Modifier.size(411.dp, 600.dp),
                )
            }
        }

        val selectable = state.boardTiles.filter { it.isSelectable }
        assertTrue(selectable.isNotEmpty())
        selectable.forEach { composeRule.onNodeWithTag(tileTestTag(it.id)).assertIsDisplayed() }
        composeRule.onNodeWithTag(tileTestTag(selectable.first().id)).performClick()
        assertTrue(tapped == listOf(selectable.first().id))
    }
}
