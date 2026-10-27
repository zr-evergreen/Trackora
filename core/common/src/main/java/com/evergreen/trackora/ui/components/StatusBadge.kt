package com.evergreen.trackora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.evergreen.trackora.ui.theme.container
import com.evergreen.trackora.ui.theme.content
import com.evergreen.trackora.ui.theme.statusColors

/**
 * The one way a work entry's status is drawn: icon and word on a tinted pill.
 *
 * The icon means status never rests on colour alone — saffron and turquoise
 * are close enough in luminance that colour-blind users need the shape. When
 * an entry moves on, the pill blends to its new colour rather than snapping,
 * which is the visible confirmation that the tap did something.
 */
@Composable
fun StatusBadge(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.statusColors
    val container by animateColorAsState(
        targetValue = colors.container(status),
        animationSpec = tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.Standard),
        label = "statusContainer"
    )
    val content by animateColorAsState(
        targetValue = colors.content(status),
        animationSpec = tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.Standard),
        label = "statusContent"
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = TrackoraIcons.forStatus(status),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(id = statusLabel(status)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

/**
 * The single forward move from [currentStatus], as a button tinted with the
 * status it leads to — "Completed" in saffron, "Delivered" in turquoise — so
 * the button previews what the badge will become. Draws nothing for a
 * delivered entry, which has nowhere left to go.
 *
 * [entryTitle] is read with the label by screen readers: three of these can
 * sit on one screen, and "Delivered" alone does not say which job it acts on.
 */
@Composable
fun StatusActionButton(
    currentStatus: Status,
    entryTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = nextStatus(currentStatus) ?: return
    val colors = MaterialTheme.statusColors
    val label = stringResource(id = actionLabel(target))
    val description = stringResource(id = R.string.action_for_entry, label, entryTitle)

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colors.container(target),
            contentColor = colors.content(target)
        ),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Icon(
            imageVector = TrackoraIcons.forStatus(target),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
