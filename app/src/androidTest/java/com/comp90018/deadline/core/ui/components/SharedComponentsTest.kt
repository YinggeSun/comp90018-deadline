package com.comp90018.deadline.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.InkBlue40
import com.comp90018.deadline.core.theme.InkBlue80
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeUsesBrandColoursInsteadOfDynamicColour() {
        var lightPrimary = Color.Unspecified
        var darkPrimary = Color.Unspecified
        composeRule.setContent {
            DeadlineTheme(darkTheme = false) { lightPrimary = MaterialTheme.colorScheme.primary }
            DeadlineTheme(darkTheme = true) { darkPrimary = MaterialTheme.colorScheme.primary }
        }

        assertEquals(InkBlue40, lightPrimary)
        assertEquals(InkBlue80, darkPrimary)
    }

    @Test
    fun buttonsInvokeOnClick() {
        var clicks = 0
        composeRule.setContent {
            DeadlineTheme {
                PrimaryButton(text = "Primary", onClick = { clicks++ })
                SecondaryButton(text = "Secondary", onClick = { clicks++ })
                TertiaryButton(text = "Tertiary", onClick = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Primary").performClick()
        composeRule.onNodeWithText("Secondary").performClick()
        composeRule.onNodeWithText("Tertiary").performClick()

        assertEquals(3, clicks)
    }

    @Test
    fun disabledButtonIsNotEnabled() {
        composeRule.setContent {
            DeadlineTheme {
                PrimaryButton(text = "Play", onClick = {}, enabled = false)
            }
        }

        composeRule.onNodeWithText("Play").assertIsNotEnabled()
    }

    @Test
    fun loadingContentShowsMessage() {
        composeRule.setContent {
            DeadlineTheme { LoadingContent(message = "Loading levels") }
        }

        composeRule.onNodeWithText("Loading levels").assertIsDisplayed()
    }

    @Test
    fun errorContentRetryCallsBack() {
        var retried = false
        composeRule.setContent {
            DeadlineTheme {
                ErrorContent(message = "No connection", onRetry = { retried = true })
            }
        }

        composeRule.onNodeWithText("No connection").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(true, retried)
    }

    @Test
    fun errorContentHidesRetryWithoutCallback() {
        composeRule.setContent {
            DeadlineTheme { ErrorContent(message = "No connection") }
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }
}
