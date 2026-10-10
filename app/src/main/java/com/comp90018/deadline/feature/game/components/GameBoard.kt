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
import androidx.compose.ui.platform.testTag
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

/**
 * Share of each tile's 2 x 2 slot left empty around the drawn tile. The slot (and so the
 * overlap geometry the engine uses) is unchanged; drawing the tile a little smaller than
 * its slot lets more of the tiles underneath show, which makes the layers easier to read.
 */
private const val TILE_INSET_FRACTION = 0.12f

/**
 * Largest drawn tile. Small boards stop growing at this size instead of filling the
 * screen, so the board reads as a pile of tiles rather than a few large cards.
 */
val MaxTileSize = 56.dp

/**
 * Smallest drawn tile. Tiles can be drawn below the 48dp touch-target size because the
 * tile's tappable area is still extended to 48dp (see [TileView]).
 */
val MinTileSize = 40.dp

const val GAME_BOARD_TAG = "game_board"

/** Distance between two neighbouring tile slots for a tile drawn at [tileSize]. */
private fun slotFor(tileSize: Dp): Dp = tileSize / (1f - TILE_INSET_FRACTION)

/**
 * Smallest board that still draws every tile at [MinTileSize] or larger.
 * Screens with less room should scroll rather than shrink the board further.
 */
fun minimumBoardSize(
    rows: Int,
    columns: Int,
): DpSize {
    val unit = slotFor(MinTileSize) / TILE_SPAN
    return DpSize(width = unit * columns, height = unit * rows)
}

/**
 * Lays tiles out by their logical TilePosition, scaled to fit the available space but
 * never drawing a tile larger than [MaxTileSize]. A tile spans [TILE_SPAN] units each way,
 * so a one-unit step is a half-tile stagger. [tiles] must be ordered bottom layer first so
 * upper layers draw on top. The board is centred in the space it is given.
 */
@Composable
fun GameBoard(
    tiles: List<TileUiModel>,
    rows: Int,
    columns: Int,
    onTileClick: (tileId: String) -> Unit,
    modifier: Modifier = Modifier,
    peekAmount: Float = 0f,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (rows <= 0 || columns <= 0) return@BoxWithConstraints
        val unit: Dp = min(min(maxWidth / columns, maxHeight / rows), slotFor(MaxTileSize) / TILE_SPAN)
        val slot = unit * TILE_SPAN
        val gap = slot * TILE_INSET_FRACTION
        val topLayer = tiles.maxOfOrNull { it.layer } ?: 0
        Box(modifier = Modifier.size(unit * columns, unit * rows).testTag(GAME_BOARD_TAG)) {
            tiles.forEach { tile ->
                TileView(
                    tile = tile,
                    size = slot - gap,
                    onClick = { onTileClick(tile.id) },
                    modifier =
                        Modifier.offset(
                            x = unit * tile.column + gap / 2,
                            y = unit * tile.row + gap / 2,
                        ).tiltPeek(
                            // Peeking reveals covered tiles. A selectable tile is treated as
                            // top layer, so it is never dimmed or moved even when it sits on
                            // a lower layer, and stays easy to tell apart from covered ones.
                            layerIndex = if (tile.isSelectable) topLayer else tile.layer,
                            topLayerIndex = topLayer,
                            peekAmount = peekAmount,
                        ),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun GameBoardPreview() {
    val tiles =
        listOf(
            TileUiModel("a", TileType.BOOK, row = 0, column = 0, layer = 0, isSelectable = false),
            TileUiModel("b", TileType.BOOK, row = 0, column = 2, layer = 0, isSelectable = false),
            TileUiModel("c", TileType.COFFEE, row = 2, column = 0, layer = 0, isSelectable = true),
            TileUiModel("d", TileType.COFFEE, row = 2, column = 2, layer = 0, isSelectable = true),
            TileUiModel("e", TileType.LAPTOP, row = 0, column = 1, layer = 1, isSelectable = true),
        )
    DeadlineTheme {
        Surface {
            GameBoard(
                tiles = tiles,
                rows = 4,
                columns = 4,
                onTileClick = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
