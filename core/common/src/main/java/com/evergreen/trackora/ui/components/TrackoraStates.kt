package com.evergreen.trackora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import kotlinx.coroutines.delay

/**
 * The three states every list screen can be in, as shared components.
 *
 * All three centre in whatever space they are given and wrap their text, so
 * they survive long Persian strings and large font scales without the caller
 * doing anything.
 */

/**
 * Work is being loaded.
 *
 * The spinner waits a beat before appearing: the database usually answers in
 * a few milliseconds, and a spinner that flashes for one frame reads as
 * flicker rather than progress.
 */
@Composable
fun TrackoraLoadingState(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_GRACE_MS)
        visible = true
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(TrackoraMotion.MEDIUM))) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

/**
 * There is nothing here yet, and that is normal.
 *
 * @param title what is absent, stated plainly.
 * @param body what the user can do about it. Optional — some empty states are
 *   simply a fact and inventing an instruction for them adds noise.
 * @param icon decorative; screen readers get [title], which says everything
 *   the picture does.
 */
@Composable
fun TrackoraEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    /** A way out of the state, such as clearing a search; shown when both are given. */
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(modifier) {
        if (icon != null) {
            IconTile(
                icon = icon,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 64.dp,
                shape = CircleShape
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (body != null) {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.size(20.dp))
            FilledTonalButton(onClick = onAction) {
                Text(text = actionLabel)
            }
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
    StateLayout(modifier) {
        IconTile(
            icon = TrackoraIcons.Error,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            size = 64.dp,
            shape = CircleShape
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(20.dp))
        FilledTonalButton(onClick = onRetry) {
            Icon(
                imageVector = TrackoraIcons.Retry,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = retryLabel)
        }
    }
}

/** Centres a state and lets it rise into place instead of popping in. */
@Composable
private fun StateLayout(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = fadeIn(tween(TrackoraMotion.MEDIUM)) +
            slideInVertically(tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.EmphasizedDecelerate)) { it / 12 },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

private const val LOADING_GRACE_MS = 300L
