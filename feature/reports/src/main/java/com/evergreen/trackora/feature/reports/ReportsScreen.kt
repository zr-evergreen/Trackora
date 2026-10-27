package com.evergreen.trackora.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.feature.reports.R
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraFilterChip
import com.evergreen.trackora.ui.components.TrackoraFilterRow
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraScreenContainer
import com.evergreen.trackora.ui.text.localizedNumber

/**
 * Reports screen for viewing numeric summaries.
 */

private enum class ReportsRange {
    DAILY, WEEKLY, MONTHLY
}

@Composable
fun ReportsScreen(
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    viewModel: ReportsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    TrackoraScreenContainer(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Text(
            text = stringResource(id = R.string.reports_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isLoading) {
            TrackoraLoadingState()
        } else if (uiState.errorMessage != null) {
            TrackoraErrorState(
                title = stringResource(id = com.evergreen.trackora.common.R.string.state_error_title),
                message = stringResource(id = com.evergreen.trackora.common.R.string.state_error_body),
                retryLabel = stringResource(id = com.evergreen.trackora.common.R.string.state_retry),
                onRetry = viewModel::retry
            )
        } else {
            var selectedRange by rememberSaveable { mutableStateOf(ReportsRange.DAILY) }

            RangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            val currentSummary: ReportSummary
            val rangeLabel: String
            when (selectedRange) {
                ReportsRange.DAILY -> {
                    currentSummary = uiState.daily
                    rangeLabel = stringResource(id = R.string.reports_daily)
                }
                ReportsRange.WEEKLY -> {
                    currentSummary = uiState.weekly
                    rangeLabel = stringResource(id = R.string.reports_weekly)
                }
                ReportsRange.MONTHLY -> {
                    currentSummary = uiState.monthly
                    rangeLabel = stringResource(id = R.string.reports_monthly)
                }
            }

            Text(
                text = rangeLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!currentSummary.hasActivity && uiState.undeliveredCount == 0) {
                // A grid of zeros looks like a broken screen. Say what is
                // actually true: nothing was recorded in this window.
                TrackoraEmptyState(
                    title = stringResource(id = R.string.reports_empty_title),
                    body = stringResource(id = R.string.reports_empty_body)
                )
            } else {
                ReportsFigures(
                    summary = currentSummary,
                    undeliveredCount = uiState.undeliveredCount
                )
            }
        }
    }
}

@Composable
private fun RangeFilterRow(
    selectedRange: ReportsRange,
    onRangeSelected: (ReportsRange) -> Unit
) {
    TrackoraFilterRow {
        TrackoraFilterChip(
            label = stringResource(id = R.string.reports_daily),
            selected = selectedRange == ReportsRange.DAILY,
            onClick = { onRangeSelected(ReportsRange.DAILY) }
        )
        TrackoraFilterChip(
            label = stringResource(id = R.string.reports_weekly),
            selected = selectedRange == ReportsRange.WEEKLY,
            onClick = { onRangeSelected(ReportsRange.WEEKLY) }
        )
        TrackoraFilterChip(
            label = stringResource(id = R.string.reports_monthly),
            selected = selectedRange == ReportsRange.MONTHLY,
            onClick = { onRangeSelected(ReportsRange.MONTHLY) }
        )
    }
}

/**
 * The figures for one window.
 *
 * ### What this replaces
 *
 * The previous card printed Completed, then Delivered, then Completed again
 * and Total quantity — the same number twice in one card, in two different
 * type sizes. It also had no empty state, so a user with no data saw a grid of
 * zeros rather than an explanation.
 *
 * The figures now answer the questions a self-employed worker actually asks:
 * how much did I finish, how many units was that, how does it compare with the
 * period before, and — the one that costs money — how much is still sitting
 * undelivered.
 */
@Composable
private fun ReportsFigures(summary: ReportSummary, undeliveredCount: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            // Intrinsic min height so a two-line delta on one tile does not
            // leave the other visibly short beside it.
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile(
                label = stringResource(id = R.string.reports_completed),
                value = localizedNumber(summary.completed),
                delta = summary.changeVsPrevious,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(id = R.string.reports_quantity),
                value = localizedNumber(summary.totalQuantity),
                modifier = Modifier.weight(1f)
            )
        }

        // Present-tense, not scoped to the selected window, so it is separated
        // from the two figures above rather than sitting in the same row.
        if (undeliveredCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.reports_awaiting_delivery),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = localizedNumber(undeliveredCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

/**
 * One figure, with an optional change against the previous period.
 *
 * The delta is plain text rather than a coloured arrow: for a pieceworker a
 * quiet month is information, not a failure, and painting it red would be the
 * app passing judgement on their week.
 */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: Int? = null,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (delta != null && delta != 0) {
                Text(
                    text = stringResource(
                        id = if (delta > 0) R.string.reports_delta_up else R.string.reports_delta_down,
                        localizedNumber(kotlin.math.abs(delta))
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
