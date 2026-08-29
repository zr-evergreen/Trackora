package com.evergreen.trackora.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.localizedDate

/**
 * The one way a work entry is drawn in a list.
 *
 * Today and All Work previously had separate row implementations of the same
 * concept, and they had drifted apart in every respect that matters: different
 * card padding and elevation, different title weight and line limit, different
 * status rendering — All Work still used a pill built from three hardcoded
 * Material 2 hexes — and only one of them showed a date. A user moving between
 * the two tabs was looking at two designs for one thing.
 *
 * ### Layout rule
 *
 * The title owns its line. Everything else sits below it in a [FlowRow] that
 * wraps rather than squeezes: status, quantity, date, and at most one action.
 * The previous All Work row put title, quantity and status on one line with
 * `SpaceBetween`, which is why a 320dp screen truncated the title to a few
 * characters and a 1.5x font scale blew the card open with a void in it.
 *
 * ### One action, not three
 *
 * There is only ever one forward move in the lifecycle — in progress becomes
 * completed, completed becomes delivered, delivered is done — so the row offers
 * one button rather than asking the user to re-pick the state they are already
 * looking at.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkEntryRow(
    entry: WorkEntry,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAdvanceStatus: (() -> Unit)? = null,
    /**
     * Whether to show the entry's own date.
     *
     * True on history-style lists, where entries span months and a row without
     * a date is unplaceable. False on Today, where every row is by definition
     * today and repeating the date on each one is noise.
     */
    showDate: Boolean = false,
    /**
     * Whether this row offers its forward action.
     *
     * Today passes false for completed work, because that work already sits in
     * the undelivered band above with a Deliver button, and the same action
     * twice on one screen makes the band look decorative.
     */
    showAction: Boolean = true,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                // Title, status, quantity and date describe one thing, so a
                // screen reader should announce them as one stop rather than
                // making the user swipe through four fragments per row.
                .semantics(mergeDescendants = true) {}
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall.forUserContent(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadge(status = entry.status)

                entry.quantity?.let { quantity ->
                    RowMeta(
                        text = stringResource(id = R.string.work_qty_label, quantity),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                if (showDate) {
                    RowMeta(
                        text = localizedDate(entry.date),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                val action = onAdvanceStatus
                if (action != null && showAction) {
                    nextActionLabel(entry.status)?.let { labelRes ->
                        val label = stringResource(id = labelRes)
                        TextButton(
                            onClick = action,
                            modifier = Modifier
                                // Keeps the tap target at the 48dp minimum even
                                // when the Persian label is short.
                                .widthIn(min = 88.dp)
                                // Names the entry, so the control still says
                                // what it acts on when read out of context.
                                .semantics {
                                    contentDescription = "$label، ${entry.title}"
                                }
                        ) {
                            Text(text = label)
                        }
                    }
                }
            }
        }
    }
}

/** Secondary text inside the meta row, so quantity and date always match. */
@Composable
private fun RowMeta(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** The single forward move available from [status], or null when it is terminal. */
private fun nextActionLabel(status: Status): Int? = when (status) {
    Status.IN_PROGRESS -> R.string.action_mark_complete
    Status.COMPLETED -> R.string.action_mark_delivered
    Status.DELIVERED -> null
}
