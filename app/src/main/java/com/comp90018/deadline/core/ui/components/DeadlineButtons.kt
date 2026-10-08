package com.comp90018.deadline.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.core.theme.Dimens
import com.comp90018.deadline.core.theme.Spacing

/** Main call to action on a screen, such as Play or Replay. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(Dimens.buttonMinWidth, Dimens.buttonMinHeight),
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

/** Alternative action shown next to a [PrimaryButton], such as Settings or Home. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(Dimens.buttonMinWidth, Dimens.buttonMinHeight),
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

/** Low-emphasis action, such as Back or Cancel. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Dimens.buttonMinHeight),
        enabled = enabled,
    ) {
        Text(text = text)
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeadlineButtonsPreview() {
    DeadlineTheme {
        Surface {
            Column(modifier = Modifier.padding(Spacing.large)) {
                PrimaryButton(text = "Play", onClick = {})
                SecondaryButton(text = "Settings", onClick = {})
                TertiaryButton(text = "Back", onClick = {})
                PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }
    }
}
