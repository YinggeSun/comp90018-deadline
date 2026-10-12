package com.comp90018.deadline.integration

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.MainActivity
import com.comp90018.deadline.app.DeadlineApp
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
import org.junit.runner.RunWith

/** Real MainActivity, generated boards, Android Back dispatcher and production settings storage. */
@RunWith(AndroidJUnit4::class)
class MainActivityAcceptanceTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val repository get() = ApplicationProvider.getApplicationContext<DeadlineApp>().container.settingsRepository
    private var original: PlayerSettings? = null

    @Before
    fun setUp() {
        original = runBlocking { repository.settings.first() }
        runBlocking { repository.update { PlayerSettings() } }
    }

    @After
    fun tearDown() {
        original?.let { saved -> runBlocking { repository.update { saved } } }
    }

    private fun click(label: String) {
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun realGameSupportsUndoRestartAndSystemBack() {
        rule.onNodeWithText("Deadline!").assertIsDisplayed()
        UiScreenshot.capture(rule, "home")
        click("Play")
        rule.onNodeWithTag(levelCardTestTag("level_1")).assertIsDisplayed()
        UiScreenshot.capture(rule, "level-select")
        rule.onNodeWithTag(levelCardTestTag("level_1")).performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "game_board")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Undo").performScrollTo().assertIsNotEnabled()
        UiScreenshot.capture(rule, "game")
        val selectableTile =
            SemanticsMatcher("Selectable board tile") {
                it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("tile_") &&
                    !it.config.contains(SemanticsProperties.Disabled)
            }
        val tileTag = rule.onAllNodes(selectableTile).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
        rule.onNodeWithTag(tileTag).performScrollTo().performClick()
        rule.onNodeWithTag(tileTag).assertDoesNotExist()
        rule.onNodeWithText("Undo").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithTag(tileTag).assertExists()
        rule.onNodeWithText("Undo").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(tileTag).performScrollTo().performClick()
        click("Restart")
        rule.onNodeWithTag(tileTag).assertExists()
        rule.onNodeWithText("Undo").assertIsNotEnabled()
        systemBack()
        rule.onNodeWithTag(levelCardTestTag("level_1")).assertExists()
        systemBack()
        rule.onNodeWithText("Deadline!").assertExists()
    }

    @Test
    fun allSettingsSwitchesPersistAcrossActivityRecreation() {
        click("Settings")
        val labels = listOf("Background Music", "Sound Effects", "Vibration", "Shake to shuffle", "Tilt to peek")
        labels.forEach(::click)
        val expected = PlayerSettings(null, false, false, false, false, false)
        rule.waitUntil(5_000) { runBlocking { repository.settings.first() } == expected }
        UiScreenshot.capture(rule, "settings")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        labels.forEach { rule.onNodeWithText(it).performScrollTo().assertIsOff() }
        assertEquals(expected, runBlocking { repository.settings.first() })
        UiScreenshot.capture(rule, "settings-recreated")
        systemBack()
        rule.onNodeWithText("Deadline!").assertExists()
    }
}
