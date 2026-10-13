package com.comp90018.deadline.feature.leaderboard

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.CampusBackgrounds
import com.comp90018.deadline.core.ui.components.BackdropStrength
import com.comp90018.deadline.core.ui.components.CenteredScrollableColumn
import com.comp90018.deadline.core.ui.components.ErrorContent
import com.comp90018.deadline.core.ui.components.LoadingContent
import com.comp90018.deadline.core.ui.components.PhotoBackground
import com.comp90018.deadline.core.ui.components.ScreenTopBar
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.core.util.TimeFormatter
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure

/** Test tag of the row ranked at [index] (0 = top), for UI tests. */
fun leaderboardRowTestTag(index: Int) = "leaderboard_row_$index"

const val LEADERBOARD_OFFLINE_BANNER_TAG = "leaderboard_offline_banner"

@Composable
fun LeaderboardScreen(
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = viewModel(factory = LeaderboardViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    PhotoBackground(image = CampusBackgrounds.forLevel(uiState.selectedLevelId), strength = BackdropStrength.Strong) {
        LeaderboardContent(
            uiState = uiState,
            onLevelSelected = viewModel::selectLevel,
            onRetry = viewModel::retry,
            onBack = onBack,
        )
    }
}

/** Stateless body of the Leaderboard screen, driven only by [uiState]. */
@Composable
fun LeaderboardContent(
    uiState: LeaderboardUiState,
    onLevelSelected: (levelId: String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(R.string.leaderboard_title), onBack = onBack)

        val selectedIndex = uiState.levels.indexOfFirst { it.id == uiState.selectedLevelId }.coerceAtLeast(0)
        if (uiState.levels.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = Color.Transparent,
                edgePadding = Spacing.large,
            ) {
                uiState.levels.forEachIndexed { index, level ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onLevelSelected(level.id) },
                        text = { Text(level.name) },
                    )
                }
            }
        }

        val levelName = uiState.levels.getOrNull(selectedIndex)?.name.orEmpty()
        when (val content = uiState.content) {
            LeaderboardContent.Loading -> LoadingContent(message = stringResource(R.string.leaderboard_loading))
            is LeaderboardContent.Ranked ->
                if (content.rows.isEmpty()) {
                    EmptyRanking(levelName)
                } else {
                    Ranking(content.rows)
                }
            is LeaderboardContent.Offline ->
                if (content.rows.isEmpty()) {
                    ErrorContent(
                        title = stringResource(R.string.leaderboard_offline_title),
                        message = stringResource(R.string.leaderboard_offline_message),
                        onRetry = onRetry,
                    )
                } else {
                    OfflineBanner(onRetry = onRetry)
                    Ranking(content.rows)
                }
            is LeaderboardContent.Error ->
                ErrorContent(message = stringResource(content.reason.messageRes()), onRetry = onRetry)
        }
    }
}

private fun LeaderboardFailure.messageRes(): Int =
    when (this) {
        LeaderboardFailure.NOT_SIGNED_IN -> R.string.leaderboard_error_not_signed_in
        LeaderboardFailure.REJECTED -> R.string.leaderboard_error_rejected
        LeaderboardFailure.UNKNOWN -> R.string.leaderboard_error_unknown
    }

@Composable
private fun EmptyRanking(levelName: String) {
    CenteredScrollableColumn {
        Text(
            text = stringResource(R.string.leaderboard_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.leaderboard_empty_message, levelName),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Says the ranking may be out of date; Retry reconnects if the live ranking does not resume by itself. */
@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.large, end = Spacing.large, top = Spacing.medium)
                .testTag(LEADERBOARD_OFFLINE_BANNER_TAG),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.large, end = Spacing.small, top = Spacing.small, bottom = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.leaderboard_offline_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TertiaryButton(text = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun Ranking(rows: List<LeaderboardRowUiModel>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        items(rows.size) { index ->
            RankingRow(row = rows[index], modifier = Modifier.testTag(leaderboardRowTestTag(index)))
        }
    }
}

/** One player's placing. The player's own row is filled and labelled "You", not told apart by colour alone. */
@Composable
private fun RankingRow(
    row: LeaderboardRowUiModel,
    modifier: Modifier = Modifier,
) {
    val time = TimeFormatter.formatHundredths(row.timeMillis)
    val description =
        stringResource(
            if (row.isCurrentPlayer) R.string.leaderboard_row_description_you else R.string.leaderboard_row_description,
            row.rank,
            row.nickname,
            time,
        )
    Surface(
        color = if (row.isCurrentPlayer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor =
            if (row.isCurrentPlayer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border =
            BorderStroke(
                width = 1.dp,
                color = if (row.isCurrentPlayer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Text(
                text = row.rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (row.isCurrentPlayer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = RANK_MIN_WIDTH),
            )
            Text(
                text = row.nickname,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.isCurrentPlayer) {
                Text(
                    text = stringResource(R.string.leaderboard_you),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(text = time, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Fits a three-digit rank, so names line up in the top 50. */
private val RANK_MIN_WIDTH = 32.dp

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LeaderboardPreview() {
    DeadlineTheme {
        Surface {
            LeaderboardContent(
                uiState =
                    LeaderboardUiState(
                        levels = (1..6).map { LeaderboardLevelTab("level_$it", "Level $it") },
                        selectedLevelId = "level_2",
                        content =
                            LeaderboardContent.Ranked(
                                listOf(
                                    LeaderboardRowUiModel(1, "Alex", 41_230, isCurrentPlayer = false),
                                    LeaderboardRowUiModel(2, "Hao", 52_070, isCurrentPlayer = true),
                                    LeaderboardRowUiModel(2, "Sam", 52_070, isCurrentPlayer = false),
                                    LeaderboardRowUiModel(4, "A very long nickname here", 75_900, isCurrentPlayer = false),
                                ),
                            ),
                    ),
                onLevelSelected = {},
                onRetry = {},
                onBack = {},
            )
        }
    }
}

@Preview(name = "Offline", showBackground = true)
@Composable
private fun LeaderboardOfflinePreview() {
    DeadlineTheme {
        Surface {
            LeaderboardContent(
                uiState =
                    LeaderboardUiState(
                        levels = (1..6).map { LeaderboardLevelTab("level_$it", "Level $it") },
                        selectedLevelId = "level_1",
                        content = LeaderboardContent.Offline(listOf(LeaderboardRowUiModel(1, "Alex", 41_230, false))),
                    ),
                onLevelSelected = {},
                onRetry = {},
                onBack = {},
            )
        }
    }
}
