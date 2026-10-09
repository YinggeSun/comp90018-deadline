package com.comp90018.deadline.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.tileTestTag
import com.comp90018.deadline.feature.levelselect.levelCardTestTag
import com.comp90018.deadline.navigation.AppNavHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real app container and DataStore: winning a level is saved in the background and
 * reflected on Level Select. Assertions hold whatever progress earlier runs left behind.
 */
@RunWith(AndroidJUnit4::class)
class ProgressFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val level = FixedLevels.LEVEL_1

    @Before
    fun setUp() {
        composeRule.setContent { AppNavHost() }
    }

    private fun click(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun winningLevelOneSavesBestTimeAndUnlocksWeekTwo() {
        click("Play")
        click(level.name)
        level.board.tiles.forEach { tile ->
            composeRule.onNodeWithTag(tileTestTag(tile.id)).performSemanticsAction(SemanticsActions.OnClick)
            composeRule.waitForIdle()
        }
        // The win is saved in the background after the Game screen closes. Wait for the save
        // itself first, then for Level Select (which loads progress asynchronously) to show it,
        // so a slow emulator does not fail either step.
        val app = ApplicationProvider.getApplicationContext<DeadlineApp>()
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            app.container.currentProgress.value.bestFor(level.id) != null
        }
        click("Level Select")
        composeRule.waitUntil(timeoutMillis = ASYNC_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(
                hasTestTag(levelCardTestTag(level.id)) and hasText("Best", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(levelCardTestTag(FixedLevels.LEVEL_2.id)).assertIsEnabled()

        val progress = runBlocking { app.container.progressRepository.progress.first() }
        assertTrue(level.id in progress.completedLevelIds)
        assertTrue(progress.isWeekUnlocked(2))
        assertNotNull(progress.bestFor(level.id))
    }

    private companion object {
        const val ASYNC_TIMEOUT_MILLIS = 10_000L
    }
}
