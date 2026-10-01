package com.comp90018.deadline.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.comp90018.deadline.R
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Spacing

/** Full-area loading state, shown while a screen waits for its data. */
@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.status_loading)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.large, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Full-area error state. Shows a Retry button only when [onRetry] is given,
 * so screens without a way to recover can still use it.
 */
@Composable
fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.status_error_title),
    onRetry: (() -> Unit)? = null,
    retryLabel: String = stringResource(R.string.action_retry)
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            PrimaryButton(text = retryLabel, onClick = onRetry)
        }
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun LoadingContentPreview() {
    DeadlineTheme {
        Surface { LoadingContent() }
    }
}

@Preview(name = "Error", showBackground = true)
@Preview(name = "Error dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ErrorContentPreview() {
    DeadlineTheme {
        Surface {
            ErrorContent(message = "Could not load the leaderboard.", onRetry = {})
        }
    }
}
