package com.comp90018.deadline.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = InkBlue40,
        onPrimary = Paper,
        primaryContainer = InkBlue90,
        onPrimaryContainer = InkBlue10,
        secondary = Highlighter40,
        onSecondary = Paper,
        secondaryContainer = Highlighter90,
        onSecondaryContainer = Highlighter10,
        tertiary = StickyNote40,
        onTertiary = Paper,
        tertiaryContainer = StickyNote90,
        onTertiaryContainer = StickyNote10,
        background = Paper,
        onBackground = InkText,
        surface = Paper,
        onSurface = InkText,
        surfaceVariant = PaperVariant,
        onSurfaceVariant = InkTextVariant,
        outline = PencilOutline,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = InkBlue80,
        onPrimary = InkBlue20,
        primaryContainer = InkBlue30,
        onPrimaryContainer = InkBlue90,
        secondary = Highlighter80,
        onSecondary = Highlighter20,
        secondaryContainer = Highlighter30,
        onSecondaryContainer = Highlighter90,
        tertiary = StickyNote80,
        onTertiary = StickyNote20,
        tertiaryContainer = StickyNote30,
        onTertiaryContainer = StickyNote90,
        background = Chalkboard,
        onBackground = ChalkText,
        surface = Chalkboard,
        onSurface = ChalkText,
        surfaceVariant = ChalkboardVariant,
        onSurfaceVariant = ChalkTextVariant,
        outline = ChalkOutline,
    )

/**
 * App-wide Material 3 theme. Wrap every screen in this so colours, type,
 * and shapes come from one place.
 *
 * Dynamic colour is off by default so the game keeps its own palette on
 * Android 12+ instead of taking the wallpaper colours.
 */
@Composable
fun DeadlineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
