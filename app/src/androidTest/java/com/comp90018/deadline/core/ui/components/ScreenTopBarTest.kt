package com.comp90018.deadline.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun titleIsAHeadingAndBackIsSent() {
        var backs = 0
        composeRule.setContent {
            DeadlineTheme { ScreenTopBar(title = "Level Select", onBack = { backs++ }) }
        }

        composeRule.onNodeWithText("Level Select").assertIsDisplayed().assert(isHeading)
        composeRule.onNodeWithText("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun longTitleShortensInsteadOfPushingBackOrActionsOffScreen() {
        val barWidth = 320.dp
        composeRule.setContent {
            DeadlineTheme {
                Box(modifier = Modifier.width(barWidth).testTag("bar")) {
                    ScreenTopBar(title = "A very long screen title that cannot possibly fit", onBack = {}) {
                        TertiaryButton(text = "Action", onClick = {})
                    }
                }
            }
        }

        val bar = composeRule.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
        listOf("Back", "Action").forEach { label ->
            val bounds = composeRule.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$label is cut off", bounds.left >= bar.left && bounds.right <= bar.right)
        }
    }
}
