package com.comp90018.deadline.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferProgressSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var gotCode = false
    private var redeemed = false
    private val typed = mutableListOf<String>()

    private fun show(state: TransferProgressUiState) {
        composeRule.setContent {
            DeadlineTheme {
                TransferProgressContent(
                    uiState = state,
                    onGetCode = { gotCode = true },
                    onCodeInputChange = { typed += it },
                    onRedeem = { redeemed = true },
                )
            }
        }
    }

    @Test
    fun withoutACodeTheGetButtonCreatesOne() {
        show(TransferProgressUiState())

        composeRule.onNodeWithText("Get a transfer code").performClick()

        assertTrue(gotCode)
    }

    @Test
    fun whileCreatingTheButtonSaysSoAndIsDisabled() {
        show(TransferProgressUiState(isCreating = true))

        composeRule.onNodeWithText("Creating your code…").assertIsNotEnabled()
    }

    @Test
    fun aCreatedCodeIsShownWithCopyAndExpiry() {
        show(TransferProgressUiState(code = "K7QM-3XPD", expiresAtMillis = 1_791_715_200_000))

        composeRule.onNodeWithTag(TRANSFER_CODE_TAG).assertTextContains("K7QM-3XPD")
        composeRule.onNodeWithText("Copy code").performClick()
        composeRule.onNodeWithText("Code copied.").assertExists()
        composeRule.onNodeWithText("Works until", substring = true).assertExists()
    }

    @Test
    fun transferIsDisabledUntilACodeIsComplete() {
        show(TransferProgressUiState(codeInput = "K7QM"))
        composeRule.onNodeWithText("Transfer").assertIsNotEnabled()
    }

    @Test
    fun aCompleteCodeCanBeTransferred() {
        show(TransferProgressUiState(codeInput = "K7QM-3XPD"))

        composeRule.onNodeWithText("Transfer").assertIsEnabled().performClick()

        assertTrue(redeemed)
    }

    @Test
    fun typingIsPassedToTheViewModel() {
        show(TransferProgressUiState())

        composeRule.onNodeWithTag(TRANSFER_INPUT_TAG).performTextInput("k")

        assertEquals(listOf("k"), typed)
    }

    @Test
    fun resultsAreExplained() {
        show(TransferProgressUiState(message = TransferMessage.REDEEMED, clearedLevels = 2, unlockedWeek = 5))
        composeRule
            .onNodeWithTag(TRANSFER_MESSAGE_TAG)
            .assertTextContains("Progress transferred. You now have 2 of 6 levels cleared, up to Week 5.")
    }

    @Test
    fun anExpiredCodeIsExplained() {
        show(TransferProgressUiState(codeInput = "K7QM-3XPD", message = TransferMessage.NOT_FOUND))
        composeRule.onNodeWithTag(TRANSFER_MESSAGE_TAG).assertTextContains("It may have expired", substring = true)
    }
}
