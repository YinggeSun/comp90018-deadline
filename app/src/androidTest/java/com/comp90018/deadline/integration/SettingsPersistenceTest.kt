package com.comp90018.deadline.integration

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.MainActivity
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.settings.PlayerSettings
import com.comp90018.deadline.feature.settings.NICKNAME_FIELD_TAG
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app with its DataStore: changes made on the Settings screen are stored, and a
 * fresh Settings screen (new ViewModel) reads them back. The device's previous settings
 * are restored afterwards.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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

    private fun openSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Vibration")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun stored() = runBlocking { repository.settings.first() }

    @Test
    fun changesAreStoredAndReadBackByNewSettingsScreen() {
        openSettings()

        composeRule.onNodeWithText("Vibration").performClick()
        composeRule.onNodeWithText("Tilt to peek").performScrollTo().performClick()
        composeRule.onNodeWithTag(NICKNAME_FIELD_TAG).performScrollTo().performTextReplacement("Tester")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { stored().nickname == "Tester" && !stored().tiltToPeekEnabled }
        assertEquals(PlayerSettings(nickname = "Tester", hapticsEnabled = false, tiltToPeekEnabled = false), stored())

        // Leaving clears the Settings ViewModel, so the next one can only read from storage.
        composeRule.onNodeWithText("Back").performClick()
        openSettings()

        composeRule.onNodeWithText("Vibration").assertIsOff()
        composeRule.onNodeWithText("Shake to shuffle").assertIsOn()
        composeRule.onNodeWithText("Tilt to peek").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Saved").performScrollTo()
    }
}
