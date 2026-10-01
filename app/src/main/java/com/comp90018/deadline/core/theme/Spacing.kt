package com.comp90018.deadline.core.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale. Use these instead of raw dp values for padding and
 * gaps so screens line up with each other.
 *
 * - [screenPadding]: outer padding of a screen's content
 * - [large] / [extraLarge]: between sections
 * - [medium]: between related items, such as stacked buttons
 * - [small] / [extraSmall]: inside a component, such as icon to label
 */
object Spacing {
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val screenPadding = 24.dp
}

/** Shared component sizes. */
object Dimens {
    /** Minimum height of the shared buttons; also meets the 48dp touch target. */
    val buttonMinHeight = 48.dp
    val buttonMinWidth = 200.dp
}
