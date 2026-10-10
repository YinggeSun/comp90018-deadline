package com.comp90018.deadline.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val PHOTO_BACKGROUND_TAG = "photo_background"

/**
 * How much a background photo is toned down. The overlay uses the theme's background
 * colour, so it is light in light mode and dark in dark mode.
 */
enum class BackdropStrength(val overlayAlpha: Float, val blur: Dp) {
    /** Home: the photo is the point of the screen. */
    Light(overlayAlpha = 0.55f, blur = 0.dp),

    /** Result and other text screens. */
    Medium(overlayAlpha = 0.78f, blur = 0.dp),

    /** Gameplay: the board must read as clearly as on the plain background. */
    Strong(overlayAlpha = 0.86f, blur = 6.dp),
}

/**
 * Fills the screen with [image], cropped to fit and toned down by [strength], behind
 * [content]. With no image it is just the plain theme background. The photo is
 * decorative, so screen readers skip it. Blur needs Android 12+; older versions rely
 * on the overlay alone.
 */
@Composable
fun PhotoBackground(
    @DrawableRes image: Int?,
    strength: BackdropStrength,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.fillMaxSize().background(background)) {
        if (image != null) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .testTag(PHOTO_BACKGROUND_TAG)
                        .blur(strength.blur),
            )
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(background.copy(alpha = strength.overlayAlpha)),
            )
        }
        content()
    }
}
