package com.comp90018.deadline.feature.game.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.feature.game.TileUiModel

/** Test tag of the tile with [tileId], for UI tests. */
fun tileTestTag(tileId: String) = "tile_$tileId"

/**
 * Covered tiles are faded on top of the board's Tilt-to-Peek fading, so this stays light
 * enough that the layers underneath remain readable.
 */
private const val BLOCKED_TILE_ALPHA = 0.7f

/** Icon height as a share of the drawn tile. */
private const val ICON_SIZE_FRACTION = 0.5f

/**
 * One board tile. Selectable tiles are raised and tappable; covered tiles
 * are dimmed and ignore taps.
 *
 * [size] is the drawn size. The tappable area is never smaller than 48dp: the clickable
 * Surface extends its touch area around a smaller tile, and a tap only falls back to that
 * extended area when it does not land directly on another tile.
 */
@Composable
fun TileView(
    tile: TileUiModel,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tile.type.labelRes)
    val state =
        stringResource(
            if (tile.isSelectable) R.string.game_tile_selectable else R.string.game_tile_blocked,
        )
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = tile.isSelectable,
        modifier =
            modifier
                .size(size)
                .alpha(if (tile.isSelectable) 1f else BLOCKED_TILE_ALPHA)
                .testTag(tileTestTag(tile.id))
                .semantics {
                    contentDescription = label
                    stateDescription = state
                },
        shape = MaterialTheme.shapes.medium,
        color = if (tile.isSelectable) colors.surface else colors.surfaceVariant,
        border =
            BorderStroke(
                width = 1.dp,
                color = if (tile.isSelectable) colors.primary else colors.outline,
            ),
        shadowElevation = if (tile.isSelectable) 4.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Sized from the tile in dp, so the icon always fits the tile whatever the
            // system font scale is.
            val iconSize = with(LocalDensity.current) { (size * ICON_SIZE_FRACTION).toSp() }
            Text(
                text = tile.type.symbol,
                fontSize = iconSize,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TileViewPreview() {
    DeadlineTheme {
        Row(modifier = Modifier.padding(Spacing.large)) {
            TileType.entries.forEachIndexed { index, type ->
                TileView(
                    tile =
                        TileUiModel(
                            id = type.name,
                            type = type,
                            row = 0,
                            column = 0,
                            layer = 0,
                            isSelectable = index != TileType.entries.lastIndex,
                        ),
                    size = 64.dp,
                    onClick = {},
                    modifier = Modifier.padding(Spacing.extraSmall),
                )
            }
        }
    }
}
