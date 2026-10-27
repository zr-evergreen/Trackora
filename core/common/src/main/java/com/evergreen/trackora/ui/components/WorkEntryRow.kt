package com.evergreen.trackora.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.isLargeTextScale
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.cardContainer

/**
 * The one way a work entry is drawn in a list.
 *
 * A sheet of paper on the page: the card is the raised container colour with
 * a hairline edge, because a white card on the warm background is otherwise
 * barely distinguishable from it. The title owns its line and wraps to two;
 * a one-line preview of the description follows when there is one, since that
 * is usually what distinguishes two jobs with the same name.
 *
 * Status, quantity and date sit beneath in a [FlowRow] that wraps rather than
 * squeezes, and the one forward action sits beside the details, centred on
 * the card — the same arrangement as the waiting rows on Today. There is only
 * ever one forward move in the lifecycle, so the row offers one button rather
 * than asking the user to re-pick the state they are looking at.
 *
 * The descriptive parts are one accessibility node; the action stays its own,
 * so a screen reader stops once per entry and once for its button.
 */
@Composable
fun WorkEntryRow(
    entry: WorkEntry,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAdvanceStatus: (() -> Unit)? = null,
    /**
     * Whether to show the entry's own date: true where entries span days and
     * a row without one cannot be placed, false where the heading already
     * says which day it is.
     */
    showDate: Boolean = false,
    /**
     * Whether this row offers its forward action. Today passes false for
     * completed work, because that work already sits in the waiting band above
     * with a Delivered button, and the same action twice on one screen makes
     * the band look decorative.
     */
    showAction: Boolean = true,
) {
    val showButton = onAdvanceStatus != null && showAction && nextStatus(entry.status) != null
    // At large text the button and the details cannot share a line on a
    // narrow phone without squeezing the title to a word per line, so the
    // button drops beneath them at full width instead.
    val stackButton = isLargeTextScale()

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EntryDetails(entry = entry, showDate = showDate, modifier = Modifier.weight(1f))
                if (showButton && !stackButton && onAdvanceStatus != null) {
                    StatusActionButton(
                        currentStatus = entry.status,
                        entryTitle = entry.title,
                        onClick = onAdvanceStatus
                    )
                }
            }
            if (showButton && stackButton && onAdvanceStatus != null) {
                StatusActionButton(
                    currentStatus = entry.status,
                    entryTitle = entry.title,
                    onClick = onAdvanceStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        }
    }

    val cardModifier = modifier
        .fillMaxWidth()
        // A row that loses its button once delivered, or gains a line when
        // its description changes, settles into its new height rather than
        // jumping.
        .animateContentSize(animationSpec = tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.Standard))
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.cardContainer
    val border = cardBorder()

    if (onClick != null) {
        Surface(onClick = onClick, modifier = cardModifier, shape = shape, color = color, border = border) {
            content()
        }
    } else {
        Surface(modifier = cardModifier, shape = shape, color = color, border = border) {
            content()
        }
    }
}

/** Title, description preview and the status line: one accessibility stop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDetails(
    entry: WorkEntry,
    showDate: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium.forUserContent(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Full width so a title in the other script can align to its own
            // start: a Persian title in the English build sits on the right.
            modifier = Modifier.fillMaxWidth()
        )

        entry.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.forUserContent(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusBadge(
                status = entry.status,
                modifier = Modifier.align(Alignment.CenterVertically)
            )

            entry.quantity?.let { quantity ->
                MetaLabel(
                    icon = TrackoraIcons.Quantity,
                    text = stringResource(id = R.string.work_qty_label, quantity),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            if (showDate) {
                MetaLabel(
                    icon = TrackoraIcons.Calendar,
                    text = localizedDate(entry.date),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

/** Secondary detail beside the status: a small icon and its text. */
@Composable
fun MetaLabel(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
