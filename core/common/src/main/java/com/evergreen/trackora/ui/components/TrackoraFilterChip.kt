package com.evergreen.trackora.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The one filter chip in the app.
 *
 * All Work and Reports both filter a list with a row of chips, and they had
 * drifted: All Work passed `primaryContainer` explicitly while Reports took
 * `FilterChipDefaults`, whose selected colour is `secondaryContainer`. The
 * result was that the selected chip was blue on one screen and green on the
 * other — the same control, the same gesture, two different answers to "which
 * one is on".
 *
 * Selection is the app's primary colour, because selection is emphasis and the
 * app has one emphasis colour.
 */
@Composable
fun TrackoraFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier
    )
}

/**
 * A horizontally scrollable row of filter chips.
 *
 * Scrollable because Persian labels are longer than their English equivalents
 * — «در حال انجام» against "In Progress" — and on a narrow screen a fixed row
 * either truncates the last chip or squeezes them all below a usable tap size.
 */
@Composable
fun TrackoraFilterRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
