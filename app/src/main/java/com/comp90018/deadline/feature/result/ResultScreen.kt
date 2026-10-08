package com.comp90018.deadline.feature.result

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing
import com.comp90018.deadline.core.ui.components.CenteredScrollableColumn
import com.comp90018.deadline.core.ui.components.PrimaryButton
import com.comp90018.deadline.core.ui.components.SecondaryButton
import com.comp90018.deadline.core.ui.components.TertiaryButton
import com.comp90018.deadline.core.util.TimeFormatter

@Composable
fun ResultScreen(
    levelId: String,
    won: Boolean,
    elapsedMillis: Long,
    previousBestMillis: Long?,
    onRetry: () -> Unit,
    onNextLevel: (levelId: String) -> Unit,
    onLevelSelect: () -> Unit,
    onHome: () -> Unit,
    viewModel: ResultViewModel =
        viewModel(factory = ResultViewModel.factory(levelId, won, elapsedMillis, previousBestMillis)),
) {
    val uiState by viewModel.uiState.collectAsState()
    ResultContent(
        uiState = uiState,
        onRetry = onRetry,
        onNextLevel = onNextLevel,
        onLevelSelect = onLevelSelect,
        onHome = onHome,
    )
}

/**
 * Stateless body of the Result screen. After a win with a following level,
 * Next Level is the main action; otherwise Retry is.
 */
@Composable
fun ResultContent(
    uiState: ResultUiState,
    onRetry: () -> Unit,
    onNextLevel: (levelId: String) -> Unit,
    onLevelSelect: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredScrollableColumn(modifier = modifier) {
        Text(
            text = stringResource(if (uiState.won) R.string.result_won_title else R.string.result_lost_title),
            style = MaterialTheme.typography.displaySmall,
            color = if (uiState.won) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (uiState.levelName.isNotEmpty()) {
            Text(
                text = uiState.levelName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(if (uiState.won) R.string.result_won_message else R.string.result_lost_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(Spacing.small))
        ResultStat(
            label = stringResource(R.string.result_time_label),
            value = TimeFormatter.formatSeconds(uiState.elapsedMillis / 1000),
        )
        ResultStat(
            label = stringResource(R.string.result_best_label),
            value =
                uiState.bestTimeMillis?.let { TimeFormatter.formatSeconds(it / 1000) }
                    ?: stringResource(R.string.result_no_best),
        )
        if (uiState.isNewBest) {
            Text(
                text = stringResource(R.string.result_new_best),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.small))

        val nextLevelId = uiState.nextLevelId
        if (nextLevelId != null) {
            PrimaryButton(text = stringResource(R.string.result_next_level), onClick = { onNextLevel(nextLevelId) })
            SecondaryButton(text = stringResource(R.string.result_retry), onClick = onRetry)
        } else {
            PrimaryButton(text = stringResource(R.string.result_retry), onClick = onRetry)
        }
        SecondaryButton(text = stringResource(R.string.result_level_select), onClick = onLevelSelect)
        TertiaryButton(text = stringResource(R.string.result_home), onClick = onHome)
    }
}

@Composable
private fun ResultStat(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Preview(name = "Won", showBackground = true)
@Preview(name = "Won dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultWonPreview() {
    DeadlineTheme {
        Surface {
            ResultContent(
                uiState =
                    ResultUiState(
                        levelName = "Level 1",
                        won = true,
                        elapsedMillis = 83_000,
                        bestTimeMillis = 95_000,
                        isNewBest = true,
                        nextLevelId = "level_2",
                    ),
                onRetry = {},
                onNextLevel = {},
                onLevelSelect = {},
                onHome = {},
            )
        }
    }
}

@Preview(name = "Lost", showBackground = true)
@Composable
private fun ResultLostPreview() {
    DeadlineTheme {
        Surface {
            ResultContent(
                uiState = ResultUiState(levelName = "Level 3", won = false, elapsedMillis = 41_000),
                onRetry = {},
                onNextLevel = {},
                onLevelSelect = {},
                onHome = {},
            )
        }
    }
}
