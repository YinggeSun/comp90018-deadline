package com.comp90018.deadline.integration

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.comp90018.deadline.MainActivity
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.settings.PlayerSettings
import com.comp90018.deadline.feature.levelselect.levelCardTestTag
import com.comp90018.deadline.testing.UiScreenshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real MainActivity, real generated 63-tile boards and retained navigation/ViewModels. */
class LargeBoardStabilityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val container get() = ApplicationProvider.getApplicationContext<DeadlineApp>().container
    private lateinit var savedSettings: PlayerSettings
    private val boardTile =
        SemanticsMatcher("Board tile") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("tile_")
        }

    @Before
    fun openLargeGame() {
        savedSettings = runBlocking { container.settingsRepository.settings.first() }
        runBlocking {
            container.progressRepository.mergeIn(PlayerProgress(highestUnlockedWeek = 12))
            container.settingsRepository.update {
                it.copy(
                    backgroundMusicEnabled = false,
                    soundEffectsEnabled = false,
                    hapticsEnabled = false,
                )
            }
        }
        rule.onNodeWithText("Play").performScrollTo().performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        rule.onNodeWithTag(levelCardTestTag("level_6")).performClick()
        rule.waitUntil(15_000) { tags().size == 63 }
    }

    @After
    fun restoreSettings() {
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (::savedSettings.isInitialized) runBlocking { container.settingsRepository.update { savedSettings } }
    }

    private fun tags() = rule.onAllNodes(boardTile).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }.toSet()

    private fun selectableTag() =
        rule.onAllNodes(boardTile).fetchSemanticsNodes()
            .first { !it.config.contains(SemanticsProperties.Disabled) }.config[SemanticsProperties.TestTag]

    @Test
    fun rotationAndRecreationKeepTheGeneratedBoardAndSelectedTile() {
        val initial = tags()
        val selected = selectableTag()
        rule.onNodeWithTag(selected).performScrollTo().performClick()
        val remaining = initial - selected
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        rule.waitForIdle()
        assertEquals(remaining, tags())
        UiScreenshot.capture(rule, "large-board-landscape")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals(remaining, tags())
        rule.onNodeWithText("Undo").performScrollTo().performClick()
        assertEquals(initial, tags())
        rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        UiScreenshot.capture(rule, "large-board-restored")
    }

    @Test
    fun rapidDuplicateDispatchCannotConsumeTheSameTileTwice() {
        val initial = tags()
        val selected = selectableTag()
        val node = rule.onNodeWithTag(selected).fetchSemanticsNode()
        val click = checkNotNull(node.config[SemanticsActions.OnClick].action)
        // A stale input handler dispatched repeatedly must remain safe after the first selection.
        rule.runOnUiThread { repeat(100) { click() } }
        rule.waitForIdle()
        assertEquals(initial - selected, tags())
        rule.onNodeWithText("Undo").performScrollTo().performClick()
        assertEquals(initial, tags())
    }

    @Test
    fun repeatedSelectUndoRestartAndBackgroundKeepTheLargeGameUsable() {
        val initial = tags()
        repeat(20) {
            rule.onNodeWithTag(selectableTag()).performScrollTo().performClick()
            rule.onNodeWithText("Undo").performScrollTo().performClick()
            rule.onNodeWithText("Restart").performScrollTo().performClick()
            assertEquals(initial, tags())
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        Thread.sleep(1_200)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertEquals(initial, tags())
        rule.onNodeWithTag(selectableTag()).performScrollTo().performClick()
        assertEquals(62, tags().size)
        UiScreenshot.capture(rule, "large-board-after-background")
    }
}
