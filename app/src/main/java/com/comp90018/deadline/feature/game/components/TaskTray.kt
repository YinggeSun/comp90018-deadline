package com.comp90018.deadline.feature.game.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.feature.game.TileUiModel

const val TASK_TRAY_TAG = "task_tray"

/** Test tag of a tile shown in the Task Tray; distinct from the board tile tag. */
fun trayTileTestTag(tileId: String) = "tray_$tileId"

private val MaxSlotSize = 56.dp

/**
 * The Task Tray: [capacity] slots filled left to right with [tiles]. The
 * count turns to the error colour when only one free slot is left, since
 * the next unmatched tile would lose the game.
 */
@Composable
fun TaskTray(
    tiles: List<TileUiModel>,
    capacity: Int,
    modifier: Modifier = Modifier
) {
    val almostFull = capacity - tiles.size <= 1
    val description = pluralStringResource(R.plurals.game_tray_description, capacity, tiles.size, capacity)

    Column(
        modifier = modifier
            .testTag(TASK_TRAY_TAG)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                text = stringResource(R.string.game_tray_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.game_tray_count, tiles.size, capacity),
                style = MaterialTheme.typography.labelLarge,
                color = if (almostFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (capacity <= 0) return@BoxWithConstraints
            val gap = Spacing.extraSmall
            val slotSize = min((maxWidth - gap * (capacity - 1)) / capacity, MaxSlotSize)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            ) {
                repeat(capacity) { index ->
                    TraySlot(tile = tiles.getOrNull(index), size = slotSize)
                }
            }
        }
    }
}

@Composable
private fun TraySlot(tile: TileUiModel?, size: Dp) {
    val colors = MaterialTheme.colorScheme
    val emptyLabel = stringResource(R.string.game_tray_empty_slot)
    val label = tile?.let { stringResource(it.type.labelRes) } ?: emptyLabel
    Surface(
        modifier = Modifier
            .size(size)
            .then(if (tile != null) Modifier.testTag(trayTileTestTag(tile.id)) else Modifier)
            .semantics { contentDescription = label },
        shape = MaterialTheme.shapes.small,
        color = if (tile != null) colors.surface else colors.surfaceVariant,
        border = BorderStroke(1.dp, if (tile != null) colors.primary else colors.outline)
    ) {
        if (tile != null) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = tile.type.symbol, fontSize = (size.value * 0.5f).sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskTrayPreview() {
    val tiles = listOf(TileType.BOOK, TileType.BOOK, TileType.COFFEE, TileType.LAPTOP, TileType.COFFEE, TileType.LAPTOP)
        .mapIndexed { index, type -> TileUiModel("t$index", type, 0, 0, 0, isSelectable = false) }
    DeadlineTheme {
        Surface {
            TaskTray(tiles = tiles, capacity = 7, modifier = Modifier.padding(Spacing.large))
        }
    }
}
