package com.evergreen.trackora.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.isLargeTextScale
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.text.localizedNumber

/**
 * A finished job waiting to reach its customer.
 *
 * Distinct from the ordinary entry row on purpose. This row exists to be acted
 * on, so it carries exactly one action — Delivered — as a real button rather
 * than as one of three near-identical status chips. The old three-chip
 * selector made the user pick the right chip from a row that also restated the
 * status they were already looking at; here the only forward move in the
 * lifecycle is the only control.
 *
 * The completion date is shown because these entries are deliberately not from
 * today, and "how long has this been sitting here" is the question the row is
 * answering.
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
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        val stacked = isLargeTextScale()

        // At large text the label and the button cannot share a line at 320dp:
        // the button was being clipped at the card edge. Stacking keeps both at
        // full size rather than shrinking either.
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UndeliveredDetails(entry = entry)
                DeliverButton(onDeliver = onDeliver, entryTitle = entry.title, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UndeliveredDetails(entry = entry, modifier = Modifier.weight(1f))
                DeliverButton(onDeliver = onDeliver, entryTitle = entry.title)
            }
        }
    }
}

@Composable
private fun UndeliveredDetails(entry: WorkEntry, modifier: Modifier = Modifier) {
    // One stop per job rather than three: title, date and quantity describe
    // the same thing.
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleSmall.forUserContent(),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = localizedDate(entry.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            entry.quantity?.let { quantity ->
                Text(
                    text = stringResource(id = R.string.qty_label, quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeliverButton(
    onDeliver: () -> Unit,
    entryTitle: String,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(id = R.string.today_deliver)
    Button(
        onClick = onDeliver,
        modifier = modifier
            .widthIn(min = 96.dp)
            // Three of these sit on the screen at once; without the job name
            // they all announce identically.
            .semantics { contentDescription = "$label، $entryTitle" },
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1
        )
    }
}
