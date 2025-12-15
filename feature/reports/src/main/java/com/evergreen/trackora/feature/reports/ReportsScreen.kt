package com.evergreen.trackora.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.evergreen.trackora.ui.components.TrackoraScreenContainer
import com.evergreen.trackora.ui.components.TrackoraSummaryCard

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
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
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

            ReportsSummaryCard(summary = currentSummary)
        }
    }
}

@Composable
private fun RangeFilterRow(
    selectedRange: ReportsRange,
    onRangeSelected: (ReportsRange) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedRange == ReportsRange.DAILY,
            onClick = { onRangeSelected(ReportsRange.DAILY) },
            label = { Text(text = stringResource(id = R.string.reports_daily)) },
            colors = FilterChipDefaults.filterChipColors()
        )
        FilterChip(
            selected = selectedRange == ReportsRange.WEEKLY,
            onClick = { onRangeSelected(ReportsRange.WEEKLY) },
            label = { Text(text = stringResource(id = R.string.reports_weekly)) },
            colors = FilterChipDefaults.filterChipColors()
        )
        FilterChip(
            selected = selectedRange == ReportsRange.MONTHLY,
            onClick = { onRangeSelected(ReportsRange.MONTHLY) },
            label = { Text(text = stringResource(id = R.string.reports_monthly)) },
            colors = FilterChipDefaults.filterChipColors()
        )
    }
}

@Composable
private fun ReportsSummaryCard(summary: ReportSummary) {
    TrackoraSummaryCard(
        modifier = Modifier.fillMaxWidth(),
        leftTitle = stringResource(id = R.string.reports_completed),
        leftValue = summary.completed.toString(),
        rightTitle = stringResource(id = R.string.reports_delivered),
        rightValue = summary.delivered.toString()
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SummaryStat(
            label = stringResource(id = R.string.reports_completed),
            value = summary.completed.toString(),
            modifier = Modifier.weight(1f)
        )
        SummaryStat(
            label = stringResource(id = R.string.reports_quantity),
            value = summary.totalQuantity.toString(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


