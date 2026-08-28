package com.evergreen.trackora.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.StatusBadge
import com.evergreen.trackora.ui.text.forUserContent

/**
 * One work entry in a list.
 *
 * ### What this replaces
 *
 * The previous row put a status pill, a quantity and a three-button status
 * selector on the same line as the title. It said the status twice — once as a
 * pill and again as the highlighted chip — and the three chips consumed enough
 * width that on a 320dp screen the title truncated to four characters and the
 * chips wrapped into a vertical stack. At 1.5x font scale the card grew to
 * roughly 900px with a void in the middle.
 *
 * ### The rule now
 *
 * The title gets its own line. Everything else sits beneath it in a row that
 * wraps rather than squeezing: status, quantity, and at most one action.
 *
 * There is one action because there is only ever one forward move in the
 * lifecycle — in progress becomes completed, completed becomes delivered,
 * delivered is done. Offering three chips asked the user to re-select the
 * state they were already looking at; offering one button asks them to confirm
 * the thing that actually happened.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkEntryItem(
    entry: WorkEntry,
    onAdvanceStatus: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /**
     * Whether this row offers its forward action.
     *
     * Today's list passes false for completed work, because that work is
     * already sitting in the undelivered band above with a Deliver button. The
     * entry legitimately appears in both places — one asks "what do I owe a
     * customer", the other "what did I do today" — but offering the same action
     * twice on one screen is noise, and it makes the band look decorative.
     */
    showAction: Boolean = true,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall.forUserContent(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // FlowRow so a long Persian status label or a large font scale
            // pushes the action onto its own line instead of crushing the row.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadge(status = entry.status)

                entry.quantity?.let { quantity ->
                    Text(
                        text = stringResource(id = R.string.qty_label, quantity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                nextActionLabel(entry.status).takeIf { showAction }?.let { labelRes ->
                    TextButton(
                        onClick = onAdvanceStatus,
                        modifier = Modifier.widthIn(min = 88.dp)
                    ) {
                        Text(text = stringResource(id = labelRes))
                    }
                }
            }
        }
    }
}

/** The single forward move available from [status], or null when it is terminal. */
private fun nextActionLabel(status: Status): Int? = when (status) {
    Status.IN_PROGRESS -> R.string.action_mark_complete
    Status.COMPLETED -> R.string.today_deliver
    Status.DELIVERED -> null
}
