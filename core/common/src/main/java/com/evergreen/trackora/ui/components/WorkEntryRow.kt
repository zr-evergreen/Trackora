package com.evergreen.trackora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.isLargeTextScale
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.layoutStartAlign
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.ui.theme.statusAccent

/**
 * The one way a work entry is drawn in a list.
 *
 * ### Reading order
 *
 * The title first, on its own line and in the strongest type on the card.
 * Then one line of facts: the status as its icon and name in the status's
 * colour, then quantity and date in quiet grey. The one forward action sits
 * at the end, centred on the card.
 *
 * ### Status, said once
 *
 * The card used to carry two coloured pills — the status, and an action
 * tinted and labelled with the next status — so an in-progress job read
 * "In Progress" and "Completed" side by side. Now the status appears once,
 * as words, and its colour runs down a slim mark at the card's start edge
 * that the eye can scan without reading. The action is a neutral outlined
 * button that names what it does, "Mark done", with only its icon in colour.
 *
 * ### Shape
 *
 * A sheet of paper on the page: the raised container colour with a hairline
 * edge. The button sits within the height of two lines of text, so cards with
 * and without one are the same height and a list of them reads as an even
 * ledger. Titles line up with the layout's start edge in both languages; see
 * [layoutStartAlign].
 *
 * ### Motion
 *
 * The card dips slightly while pressed, the mark and label blend to the new
 * colour when the entry moves on, and the card settles into a new height
 * rather than jumping when its button goes.
 *
 * The descriptive parts are one accessibility node and the action stays its
 * own, so a screen reader stops once per entry and once for its button.
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
     * with a Mark delivered button, and the same action twice on one screen makes
     * the band look decorative.
     */
    showAction: Boolean = true,
) {
    val showButton = onAdvanceStatus != null && showAction && nextStatus(entry.status) != null
    // At large text the button and the details cannot share a line on a
    // narrow phone without squeezing the title to a word per line, so the
    // button drops beneath them at full width instead.
    val stackButton = isLargeTextScale()

    val mark by animateColorAsState(
        targetValue = statusAccent(entry.status),
        animationSpec = tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.Standard),
        label = "statusMark"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardPress"
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .drawBehind { drawStatusMark(mark) }
                .padding(start = 22.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
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
                        .padding(top = 8.dp)
                )
            }
        }
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        // A row that loses its button once delivered, or gains a line when
        // its description changes, settles into its new height rather than
        // jumping.
        .animateContentSize(animationSpec = tween(TrackoraMotion.MEDIUM, easing = TrackoraMotion.Standard))
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.cardContainer
    val border = cardBorder()

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            color = color,
            border = border,
            interactionSource = interactionSource
        ) {
            content()
        }
    } else {
        Surface(modifier = cardModifier, shape = shape, color = color, border = border) {
            content()
        }
    }
}

/**
 * The status mark: a short rounded bar inset from the card's start edge —
 * the left in English, the right in Persian — stopping short of the corners
 * so it reads as a mark on the sheet rather than a coloured border.
 */
private fun DrawScope.drawStatusMark(color: Color) {
    val width = 3.dp.toPx()
    val inset = 10.dp.toPx()
    val vertical = 14.dp.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - inset - width else inset
    drawRoundRect(
        color = color,
        topLeft = Offset(x, vertical),
        size = Size(width, (size.height - vertical * 2).coerceAtLeast(width)),
        cornerRadius = CornerRadius(width / 2)
    )
}

/** Title, description preview and the status line: one accessibility stop. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryDetails(
    entry: WorkEntry,
    showDate: Boolean,
    modifier: Modifier = Modifier,
) {
    val align = layoutStartAlign()
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium.forUserContent(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        entry.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.forUserContent(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatusLabel(
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
