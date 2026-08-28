package com.evergreen.trackora.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The three states every list screen can be in, as shared components.
 *
 * They exist as one set because the app previously had none of them
 * consistently: the Today screen hand-rolled an empty state, All Work showed a
 * bare sentence, Reports showed a card of zeros, and an error on any of the
 * three was indistinguishable from having no work — the spinner simply never
 * resolved.
 *
 * All three centre in whatever space they are given and wrap their text, so
 * they survive long Persian strings and large font scales without the caller
 * doing anything.
 */

/** Work is being loaded. Deliberately plain — a spinner is not a place for personality. */
@Composable
fun TrackoraLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * There is nothing here yet, and that is normal.
 *
 * @param title what is absent, stated plainly.
 * @param body what the user can do about it. Optional — some empty states are
 *   simply a fact and inventing an instruction for them adds noise.
 * @param icon decorative; hidden from screen readers because [title] already
 *   says everything it conveys.
 */
@Composable
fun TrackoraEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .clearAndSetSemantics { }
            )
            Spacer(12.dp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Spacer(6.dp)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Something failed.
 *
 * Always takes a retry action. An error the user can only look at is a dead
 * end, and every failure this app can actually produce — a database read — is
 * worth trying again.
 *
 * @param message what went wrong, in the user's language. Callers pass a
 *   localised string, never a raw exception message.
 */
@Composable
fun TrackoraErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(6.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(16.dp)
        OutlinedButton(onClick = onRetry) {
            Text(text = retryLabel)
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(height))
}
