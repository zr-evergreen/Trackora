package com.evergreen.trackora.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.MetaLabel
import com.evergreen.trackora.ui.components.StatusActionButton
import com.evergreen.trackora.ui.components.cardBorder
import com.evergreen.trackora.ui.isLargeTextScale
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.localizedRelativeDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.cardContainer
import java.time.LocalDate

/**
 * A finished job waiting to reach its customer, as one strip of the waiting
 * band's grouped card.
 *
 * Distinct from the ordinary entry row on purpose. This row exists to be acted
 * on, so it carries exactly one action — Delivered — as a real button rather
 * than as one of three near-identical status chips.
 *
 * The date is relative — "5 days ago" — because these entries are
 * deliberately not from today, and how long the job has been sitting on the
 * shelf is the question the row is answering.
 *
 * No confirmation dialog. Delivering is routine and frequent, and a dialog on
 * each would cost more than the rare mistake; the undo in the snackbar covers
 * that instead.
 */
@Composable
fun UndeliveredRow(
    entry: WorkEntry,
    onDeliver: () -> Unit,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        // At large text the label and the button cannot share a line at 320dp:
        // the button was being clipped at the card edge. Stacking keeps both at
        // full size rather than shrinking either.
        if (isLargeTextScale()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UndeliveredDetails(entry = entry)
                StatusActionButton(
                    currentStatus = entry.status,
                    entryTitle = entry.title,
                    onClick = onDeliver,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UndeliveredDetails(entry = entry, modifier = Modifier.weight(1f))
                StatusActionButton(
                    currentStatus = entry.status,
                    entryTitle = entry.title,
                    onClick = onDeliver
                )
            }
        }
    }
}

@Composable
private fun UndeliveredDetails(entry: WorkEntry, modifier: Modifier = Modifier) {
    // One stop per job rather than three: title, age and quantity describe
    // the same thing.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium.forUserContent(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetaLabel(
                icon = TrackoraIcons.Waiting,
                text = localizedRelativeDate(date = entry.date, today = LocalDate.now())
            )
            entry.quantity?.let { quantity ->
                MetaLabel(
                    icon = TrackoraIcons.Quantity,
                    text = stringResource(id = R.string.qty_label, quantity)
                )
            }
        }
    }
}

/** The last strip of the band: everything waiting, in the history. */
@Composable
fun SeeAllWaitingRow(
    count: Int,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.today_see_all_undelivered, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = TrackoraIcons.Forward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
