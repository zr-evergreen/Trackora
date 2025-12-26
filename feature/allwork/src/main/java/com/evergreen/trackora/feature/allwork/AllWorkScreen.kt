package com.evergreen.trackora.feature.allwork

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.StatusPill
import com.evergreen.trackora.ui.components.TrackoraScreenContainer

/**
 * Screen for viewing all work entries with quick status filters.
 */
@Composable
fun AllWorkScreen(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onEntryClick: (Long) -> Unit,
    viewModel: AllWorkViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    TrackoraScreenContainer(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Text(
            text = stringResource(id = R.string.all_work_title),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusFilterRow(
            selected = uiState.filter,
            onFilterSelected = viewModel::setFilter
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            uiState.filteredEntries.isEmpty() -> {
                Text(
                    text = stringResource(id = R.string.empty_all_work),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.filteredEntries,
                        key = { it.id }
                    ) { entry ->
                        AllWorkListItem(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterRow(
    selected: Status?,
    onFilterSelected: (Status?) -> Unit
) {
    val filters: List<Pair<String, Status?>> = listOf(
        stringResource(id = R.string.filter_all) to null,
        stringResource(id = R.string.filter_in_progress) to Status.IN_PROGRESS,
        stringResource(id = R.string.filter_completed) to Status.COMPLETED,
        stringResource(id = R.string.filter_delivered) to Status.DELIVERED
    )

    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { (label, status) ->
            FilterChip(
                selected = selected == status,
                onClick = { onFilterSelected(status) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.height(40.dp)
            )
        }
    }
}

@Composable
private fun AllWorkListItem(
    entry: WorkEntry,
    onClick: () -> Unit
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    entry.quantity?.let {
                        Text(
                            text = stringResource(id = R.string.qty_label, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StatusPill(
                    label = when (entry.status) {
                        Status.IN_PROGRESS -> stringResource(id = R.string.filter_in_progress)
                        Status.COMPLETED -> stringResource(id = R.string.filter_completed)
                        Status.DELIVERED -> stringResource(id = R.string.filter_delivered)
                    },
                    backgroundColor = when (entry.status) {
                        Status.IN_PROGRESS -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                        Status.COMPLETED -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                        Status.DELIVERED -> androidx.compose.ui.graphics.Color(0xFF2196F3)
                    }
                )
            }
        }
    }
}

