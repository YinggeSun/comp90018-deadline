package com.comp90018.deadline.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(state: SettingsUiState) {
        composeRule.setContent {
            DeadlineTheme {
                SettingsContent(
                    uiState = state,
                    onBack = { calls += "back" },
                    onHapticsChange = { calls += "haptics:$it" },
                    onShakeChange = { calls += "shake:$it" },
                    onTiltChange = { calls += "tilt:$it" },
                    onNicknameChange = { calls += "nickname:$it" },
                    onSaveNickname = { calls += "save" },
                )
            }
        }
    }

    private val loaded = SettingsUiState(isLoading = false, shakeToShuffleEnabled = false)

    @Test
    fun switchesShowStateAndToggleFromWholeRow() {
        setContent(loaded)

        composeRule.onNodeWithText("Vibration", useUnmergedTree = false).assertIsOn()
        composeRule.onNodeWithText("Shake to shuffle").assertIsOff()
        composeRule.onNodeWithText("Tilt to peek").assertIsOn()

        composeRule.onNodeWithText("Vibration").performClick()
        composeRule.onNodeWithText("Shake to shuffle").performClick()
        composeRule.onNodeWithText("Tilt to peek").performScrollTo().performClick()

        assertEquals(listOf("haptics:false", "shake:true", "tilt:false"), calls)
    }

    @Test
    fun nicknameEditsAndSaves() {
        // The field is controlled, so the test holds the draft the way the ViewModel would.
        var draft by mutableStateOf("Hao")
        composeRule.setContent {
            DeadlineTheme {
                SettingsContent(
                    uiState = loaded.copy(savedNickname = "Hao", nicknameDraft = draft),
                    onBack = {},
                    onHapticsChange = {},
                    onShakeChange = {},
                    onTiltChange = {},
                    onNicknameChange = {
                        draft = it
                        calls += "nickname:$it"
                    },
                    onSaveNickname = { calls += "save" },
                )
            }
        }
        composeRule.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(NICKNAME_FIELD_TAG).performScrollTo().performTextReplacement("Hao Xu")
        composeRule.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf("nickname:Hao Xu", "save"), calls)
    }

    @Test
    fun blankNicknameShowsErrorAndCannotBeSaved() {
        setContent(loaded.copy(savedNickname = "Hao", nicknameDraft = "   "))

        composeRule.onNodeWithText("Enter at least one letter or number.").performScrollTo()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun savedNicknameIsMarkedSaved() {
        setContent(loaded.copy(savedNickname = "Hao", nicknameDraft = "Hao"))

        composeRule.onNodeWithText("Saved").performScrollTo()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun loadingHidesControls() {
        setContent(SettingsUiState(isLoading = true))

        composeRule.onNodeWithText("Vibration").assertDoesNotExist()
        composeRule.onNodeWithText("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
