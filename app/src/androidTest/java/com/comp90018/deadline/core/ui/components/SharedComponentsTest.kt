package com.comp90018.deadline.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Dimens
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
    fun eachButtonInvokesItsOwnOnClick() {
        var primary = 0
        var secondary = 0
        var tertiary = 0
        composeRule.setContent {
            DeadlineTheme {
                Column {
                    PrimaryButton(text = "Primary", onClick = { primary++ })
                    SecondaryButton(text = "Secondary", onClick = { secondary++ })
                    TertiaryButton(text = "Tertiary", onClick = { tertiary++ })
                }
            }
        }

        composeRule.onNodeWithText("Primary").performClick()
        composeRule.onNodeWithText("Secondary").performClick()
        composeRule.onNodeWithText("Secondary").performClick()
        composeRule.onNodeWithText("Tertiary").performClick()
        composeRule.onNodeWithText("Tertiary").performClick()
        composeRule.onNodeWithText("Tertiary").performClick()

        assertEquals(1, primary)
        assertEquals(2, secondary)
        assertEquals(3, tertiary)
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

    @Test
    fun errorContentRetryStaysUsableInShortSpaceWithLargeFont() {
        var retries = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                DeadlineTheme {
                    Box(modifier = Modifier.size(width = 360.dp, height = 320.dp)) {
                        ErrorContent(
                            message = "Could not connect to the leaderboard server. " +
                                "Check your internet connection and try again in a moment.",
                            onRetry = { retries++ }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Retry")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Dimens.buttonMinHeight)
            .performClick()

        assertEquals(1, retries)
    }
}
