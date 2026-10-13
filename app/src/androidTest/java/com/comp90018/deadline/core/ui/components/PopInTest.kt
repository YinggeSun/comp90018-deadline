package com.comp90018.deadline.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopInTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentCanBeTappedBeforeTheAnimationFinishesAndIsShownAfterIt() {
        var clicks = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DeadlineTheme { TertiaryButton(text = "Pop", onClick = { clicks++ }, modifier = Modifier.popIn()) }
        }

        // First frame: the animation has only just started.
        composeRule.onNodeWithText("Pop").performClick()
        assertEquals(1, clicks)

        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Pop").assertIsDisplayed()
    }
}
