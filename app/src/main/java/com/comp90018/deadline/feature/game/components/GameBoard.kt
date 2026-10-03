package com.comp90018.deadline.feature.game.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.feature.game.GameViewModel.Companion.TILE_SPAN
import com.comp90018.deadline.feature.game.TileUiModel
import com.comp90018.deadline.feature.game.tiltPeek

/** Gap between neighbouring tiles, as a fraction of a tile. */
private const val TILE_GAP_FRACTION = 0.06f

/** Smallest drawn tile; matches the 48dp minimum touch target. */
val MinTileSize = 48.dp

/**
 * Smallest board that still draws every tile at [MinTileSize] or larger.
 * Screens with less room should scroll rather than shrink the board further.
 */
fun minimumBoardSize(rows: Int, columns: Int): DpSize {
    val unit = MinTileSize / (TILE_SPAN * (1f - TILE_GAP_FRACTION))
    return DpSize(width = unit * columns, height = unit * rows)
}

/**
 * Lays tiles out by their logical TilePosition, scaled to fit the available
 * space. A tile spans [TILE_SPAN] units each way, so a one-unit step is a
 * half-tile stagger. [tiles] must be ordered bottom layer first so upper
 * layers draw on top.
 */
@Composable
fun GameBoard(
    tiles: List<TileUiModel>,
    rows: Int,
    columns: Int,
    onTileClick: (tileId: String) -> Unit,
    modifier: Modifier = Modifier,
    peekAmount: Float = 0f
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (rows <= 0 || columns <= 0) return@BoxWithConstraints
        val unit: Dp = min(maxWidth / columns, maxHeight / rows)
        val tileSize = unit * TILE_SPAN
        val gap = tileSize * TILE_GAP_FRACTION
        Box(modifier = Modifier.size(unit * columns, unit * rows)) {
            tiles.forEach { tile ->
                TileView(
                    tile = tile,
                    size = tileSize - gap,
                    onClick = { onTileClick(tile.id) },
                    modifier = Modifier.offset(
                        x = unit * tile.column + gap / 2,
                        y = unit * tile.row + gap / 2
                    ).tiltPeek(
                        layerIndex = tile.layer,
                        topLayerIndex = tiles.maxOfOrNull { it.layer } ?: 0,
                        peekAmount = peekAmount
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun GameBoardPreview() {
    val tiles = listOf(
        TileUiModel("a", TileType.BOOK, row = 0, column = 0, layer = 0, isSelectable = false),
        TileUiModel("b", TileType.BOOK, row = 0, column = 2, layer = 0, isSelectable = false),
        TileUiModel("c", TileType.COFFEE, row = 2, column = 0, layer = 0, isSelectable = true),
        TileUiModel("d", TileType.COFFEE, row = 2, column = 2, layer = 0, isSelectable = true),
        TileUiModel("e", TileType.LAPTOP, row = 0, column = 1, layer = 1, isSelectable = true)
    )
    DeadlineTheme {
        Surface {
            GameBoard(
                tiles = tiles,
                rows = 4,
                columns = 4,
                onTileClick = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
