package com.evergreen.trackora.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.common.R as CommonR
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.text.localizedNumber
import java.time.LocalDate

/**
 * The screen the app opens on, rebuilt around the order lifecycle.
 *
 * ### What changed and why
 *
 * The previous version led with two large tiles counting today's completed and
 * delivered work. First thing in the morning both read zero, so the most
 * prominent element on the home screen was reliably a pair of zeros — and in
 * Persian a zero is a small dot, which made it look like a rendering fault.
 *
 * Worse, it could not show the one thing that matters most. It queried only
 * today, so a job finished last week and still sitting uncollected was
 * invisible. That gap between COMPLETED and DELIVERED is where a self-employed
 * worker loses money.
 *
 * The hierarchy now follows the workflow rather than the data model:
 *
 *  1. capture — quick add, at the top, always reachable
 *  2. chase   — what is finished and still owed to a customer, oldest first
 *  3. review  — what happened today, counts demoted to one quiet line
 *
 * The undelivered band is not rendered at all when empty. An empty "waiting to
 * be delivered" heading is noise on the many days when nothing is waiting, and
 * its absence is itself the good news.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(),
    onEntryClick: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = LocalDate.now()
    val snackbarHostState = remember { SnackbarHostState() }

    val advancedTitle = uiState.recentlyAdvanced?.title
    val advancedMessage = advancedTitle?.let {
        stringResource(id = R.string.today_status_changed_toast, it)
    }
    val undoLabel = stringResource(id = R.string.today_undo)

    LaunchedEffect(uiState.recentlyAdvanced) {
        if (advancedMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = advancedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoAdvance()
            else viewModel.clearRecentlyAdvanced()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(id = R.string.today_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = localizedDate(date = today, includeWeekday = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                QuickAddField(onAdd = viewModel::quickAdd)
            }

            when {
                uiState.isLoading -> TrackoraLoadingState()

                uiState.errorMessage != null -> TrackoraErrorState(
                    title = stringResource(id = CommonR.string.state_error_title),
                    message = stringResource(id = CommonR.string.state_error_body),
                    retryLabel = stringResource(id = CommonR.string.state_retry),
                    onRetry = viewModel::retry
                )

                uiState.isEmpty -> TrackoraEmptyState(
                    title = stringResource(id = R.string.today_empty_title),
                    body = stringResource(id = R.string.today_empty_body)
                )

                else -> TodayList(
                    uiState = uiState,
                    onAdvance = viewModel::advanceStatus,
                    onEntryClick = onEntryClick
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
        )
    }
}

@Composable
private fun TodayList(
    uiState: TodayUiState,
    onAdvance: (WorkEntry) -> Unit,
    onEntryClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        if (uiState.undelivered.isNotEmpty()) {
            item(key = "undelivered-header") {
                SectionHeader(
                    title = stringResource(id = R.string.today_section_undelivered),
                    count = uiState.undeliveredCount,
                    emphasised = true
                )
            }
            items(items = uiState.undelivered, key = { "u-${it.id}" }) { entry ->
                UndeliveredRow(
                    entry = entry,
                    onDeliver = { onAdvance(entry) },
                    onClick = { onEntryClick(entry.id) }
                )
            }
            item(key = "undelivered-gap") { Spacer(modifier = Modifier.height(20.dp)) }
        }

        item(key = "today-header") {
            SectionHeader(
                title = stringResource(id = R.string.today_section_today),
                count = uiState.todayEntries.size
            )
        }

        if (uiState.todayEntries.isEmpty()) {
            item(key = "today-empty") {
                Text(
                    text = stringResource(id = R.string.today_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            items(items = uiState.todayEntries, key = { "t-${it.id}" }) { entry ->
                WorkEntryItem(
                    entry = entry,
                    onAdvanceStatus = { onAdvance(entry) },
                    onClick = { onEntryClick(entry.id) },
                    // Completed work is actionable in the band above, not here.
                    showAction = entry.status == Status.IN_PROGRESS
                )
            }
            item(key = "today-summary") {
                // The counts that used to occupy two large tiles, reduced to one
                // quiet line underneath the work they describe.
                Text(
                    text = stringResource(
                        id = R.string.today_summary_line,
                        uiState.completedToday,
                        uiState.quantityToday
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * A band heading with its count.
 *
 * The count sits here rather than in a summary card so the number is attached
 * to the thing it counts — it reads as "three waiting" rather than as a
 * free-floating statistic.
 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    emphasised: Boolean = false,
) {
    val color = if (emphasised) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            text = localizedNumber(count),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
