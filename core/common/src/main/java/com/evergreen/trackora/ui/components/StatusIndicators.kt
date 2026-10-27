package com.evergreen.trackora.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.statusAccent

/**
 * A work entry's status as its icon and name, in the status's accent colour.
 *
 * No pill behind it. On a card that already has a coloured edge mark and an
 * action button, a filled badge made three coloured shapes compete for one
 * fact. The icon keeps status from resting on colour alone — saffron and
 * turquoise are close enough in luminance that colour-blind users need the
 * shape — and the colour blends to the new status when an entry moves on.
 */
@Composable
fun StatusLabel(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = statusAccent(status),
        animationSpec = tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.Standard),
        label = "statusLabel"
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = TrackoraIcons.forStatus(status),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(id = statusLabel(status)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * The single forward move from [currentStatus], as a compact outlined button
 * that says what it does — "Mark done", "Mark delivered" — with the icon of
 * the status it leads to. Draws nothing for a delivered entry, which has
 * nowhere left to go.
 *
 * Neutral on purpose. It used to be filled with the next status's colour and
 * labelled with that status's name, so an in-progress card carried two
 * coloured pills reading "In Progress" and "Completed", and the action was
 * easy to mistake for a second status. Only the icon carries the colour now.
 *
 * [entryTitle] is read with the label by screen readers: several of these can
 * sit on one screen, and "Mark delivered" alone does not say which job it
 * acts on.
 */
@Composable
fun StatusActionButton(
    currentStatus: Status,
    entryTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = nextStatus(currentStatus) ?: return
    val label = stringResource(id = actionLabel(target))
    val description = stringResource(id = R.string.action_for_entry, label, entryTitle)

    OutlinedButton(
        onClick = onClick,
        // Shorter than a full button so it sits within the card's two lines
        // of text; the touch target is still the full 48dp.
        modifier = modifier
            .heightIn(min = 36.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(start = 10.dp, end = 14.dp)
    ) {
        // Completing a job swaps "Mark done" for "Mark delivered" in place;
        // the cross-fade shows the button moved on with the entry.
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                fadeIn(tween(TrackoraMotion.MEDIUM)) togetherWith fadeOut(tween(TrackoraMotion.SHORT))
            },
            label = "nextStatus"
        ) { next ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = TrackoraIcons.forStatus(next),
                    contentDescription = null,
                    tint = statusAccent(next),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(id = actionLabel(next)),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The status one step on from [status], or null when it is terminal. */
fun nextStatus(status: Status): Status? = when (status) {
    Status.IN_PROGRESS -> Status.COMPLETED
    Status.COMPLETED -> Status.DELIVERED
    Status.DELIVERED -> null
}

private fun statusLabel(status: Status): Int = when (status) {
    Status.IN_PROGRESS -> R.string.status_in_progress
    Status.COMPLETED -> R.string.status_completed
    Status.DELIVERED -> R.string.status_delivered
}

private fun actionLabel(target: Status): Int = when (target) {
    Status.COMPLETED -> R.string.action_mark_complete
    else -> R.string.action_mark_delivered
}
