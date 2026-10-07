package com.comp90018.deadline.feature.levelselect

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.ErrorContent
import com.comp90018.deadline.core.ui.components.LoadingContent
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.core.util.TimeFormatter

/** Test tag of the level card with [levelId], for UI tests. */
fun levelCardTestTag(levelId: String) = "level_$levelId"

@Composable
fun LevelSelectScreen(
    onLevelSelected: (levelId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LevelSelectViewModel = viewModel(factory = LevelSelectViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    LevelSelectContent(uiState = uiState, onLevelSelected = onLevelSelected, onBack = onBack)
}

/** Stateless body of the Level Select screen, driven only by [uiState]. */
@Composable
fun LevelSelectContent(
    uiState: LevelSelectUiState,
    onLevelSelected: (levelId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TertiaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text(
                text = stringResource(R.string.level_select_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (uiState.isLoading) {
            LoadingContent()
        } else if (uiState.levels.isEmpty()) {
            ErrorContent(message = stringResource(R.string.level_select_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium),
            ) {
                items(uiState.levels, key = { it.id }) { level ->
                    LevelCard(level = level, onClick = { onLevelSelected(level.id) })
                }
            }
        }
    }
}

@Composable
private fun LevelCard(
    level: LevelItemUiModel,
    onClick: () -> Unit,
) {
    val lockedLabel = stringResource(R.string.level_select_locked)
    OutlinedCard(
        onClick = onClick,
        enabled = !level.isLocked,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(levelCardTestTag(level.id))
                .semantics { if (level.isLocked) stateDescription = lockedLabel },
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color = if (level.isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
            ) {
                Text(text = level.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text =
                        pluralStringResource(R.plurals.level_select_tiles, level.tileCount, level.tileCount) +
                            " · " +
                            pluralStringResource(R.plurals.level_select_layers, level.layerCount, level.layerCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        level.bestTimeSeconds
                            ?.let { stringResource(R.string.level_select_best_time, TimeFormatter.formatSeconds(it)) }
                            ?: stringResource(R.string.level_select_no_best_time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (level.isLocked) {
                Text(
                    text = lockedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LevelSelectPreview() {
    DeadlineTheme {
        Surface {
            LevelSelectContent(
                uiState =
                    LevelSelectUiState(
                        levels =
                            listOf(
                                LevelItemUiModel("level_1", "Level 1", 6, 1, isLocked = false, bestTimeSeconds = 42),
                                LevelItemUiModel("level_2", "Level 2", 9, 1, isLocked = false, bestTimeSeconds = null),
                                LevelItemUiModel("level_3", "Level 3", 9, 2, isLocked = true, bestTimeSeconds = null),
                            ),
                    ),
                onLevelSelected = {},
                onBack = {},
            )
        }
    }
}
