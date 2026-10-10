package com.comp90018.deadline.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.ui.CampusBackgrounds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoBackgroundTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsThePhotoBehindTheContent() {
        composeRule.setContent {
            DeadlineTheme {
                PhotoBackground(image = CampusBackgrounds.home, strength = BackdropStrength.Light) {
                    Text("On top")
                }
            }
        }

        composeRule.onNodeWithTag(PHOTO_BACKGROUND_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("On top").assertIsDisplayed()
    }

    @Test
    fun withoutAPhotoItIsThePlainBackground() {
        composeRule.setContent {
            DeadlineTheme {
                PhotoBackground(image = null, strength = BackdropStrength.Strong) {
                    Text("On top")
                }
            }
        }

        composeRule.onNodeWithTag(PHOTO_BACKGROUND_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("On top").assertIsDisplayed()
    }
}
