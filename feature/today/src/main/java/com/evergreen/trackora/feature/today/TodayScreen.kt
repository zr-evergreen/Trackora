package com.evergreen.trackora.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.common.R as CommonR
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.ScreenHeader
import com.evergreen.trackora.ui.components.SectionHeader
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.WorkEntryRow
import com.evergreen.trackora.ui.components.groupedItemShape
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraSpacing
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
 *
 * ### Look
 *
 * The band is one card cut into strips, headed in the saffron that marks
 * finished-but-undelivered work everywhere else in the app, so it reads as a
 * single to-do list rather than as more entries. Today's work below is the
 * ordinary entry cards under a quiet heading.
 */
@Composable
fun TodayScreen(
    /**
     * Shown by the app's Scaffold, not by this screen. Today is the one screen
     * with the add button, and a snackbar hosted here was drawn under it: the
     * button covered Undo completely, on the right in English and on the left
     * in Persian. The Scaffold places its snackbar above the button.
     */
    snackbarHostState: SnackbarHostState,
    viewModel: TodayViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(),
    onEntryClick: (Long) -> Unit = {},
    onSeeAllUndelivered: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        ScreenHeader(
            title = stringResource(id = R.string.today_title),
            subtitle = localizedDate(date = today, includeWeekday = true)
        )
        QuickAddField(
            onAdd = viewModel::quickAdd,
            modifier = Modifier.padding(
                start = TrackoraSpacing.screen,
                end = TrackoraSpacing.screen,
                bottom = TrackoraSpacing.sm
            )
        )

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
                body = stringResource(id = R.string.today_empty_body),
                icon = TrackoraIcons.Write
            )

            else -> TodayList(
                uiState = uiState,
                onAdvance = viewModel::advanceStatus,
                onEntryClick = onEntryClick,
                onSeeAllUndelivered = onSeeAllUndelivered
            )
        }
    }
}

@Composable
private fun TodayList(
    uiState: TodayUiState,
    onAdvance: (WorkEntry) -> Unit,
    onEntryClick: (Long) -> Unit,
    onSeeAllUndelivered: () -> Unit,
) {
    val listState = rememberLazyListState()
    // A lazy list holds its first visible item in place by key. So when the
    // band appears above "Today's work" — the moment the user completes a job
    // — it is inserted above the viewport and stays out of sight, with
    // nothing to say the list is scrolled. A list that was at the top stays at
    // the top instead; one the user has scrolled down keeps its place.
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    if (atTop) listState.requestScrollToItem(0)

    val rowPadding = Modifier.padding(horizontal = TrackoraSpacing.screen)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = TrackoraSpacing.sm, bottom = TrackoraSpacing.fabClearance)
    ) {
        if (uiState.undelivered.isNotEmpty()) {
            // The preview strips and the "see all" strip are one grouped card.
            val strips = uiState.undeliveredPreview.size + if (uiState.hasMoreUndelivered) 1 else 0

            item(key = "undelivered-header") {
                SectionHeader(
                    title = stringResource(id = R.string.today_section_undelivered),
                    icon = TrackoraIcons.Waiting,
                    count = uiState.undeliveredCount,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.animateItem()
                )
            }
            itemsIndexed(
                items = uiState.undeliveredPreview,
                key = { _, entry -> "u-${entry.id}" }
            ) { index, entry ->
                UndeliveredRow(
                    entry = entry,
                    onDeliver = { onAdvance(entry) },
                    onClick = { onEntryClick(entry.id) },
                    shape = groupedItemShape(index, strips),
                    modifier = Modifier
                        .animateItem()
                        .then(rowPadding)
                        .padding(vertical = 1.dp)
                )
            }
            if (uiState.hasMoreUndelivered) {
                item(key = "undelivered-more") {
                    SeeAllWaitingRow(
                        count = uiState.undeliveredCount,
                        onClick = onSeeAllUndelivered,
                        shape = groupedItemShape(strips - 1, strips),
                        modifier = Modifier
                            .animateItem()
                            .then(rowPadding)
                            .padding(vertical = 1.dp)
                    )
                }
            }

            item(key = "undelivered-gap") { Spacer(modifier = Modifier.height(TrackoraSpacing.xl)) }
        }

        item(key = "today-header") {
            SectionHeader(
                title = stringResource(id = R.string.today_section_today),
                icon = TrackoraIcons.TodaysWork,
                count = uiState.todayEntries.size,
                modifier = Modifier.animateItem()
            )
        }

        if (uiState.todayEntries.isEmpty()) {
            item(key = "today-empty") {
                Text(
                    text = stringResource(id = R.string.today_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = TrackoraSpacing.screen, vertical = TrackoraSpacing.sm)
                )
            }
        } else {
            items(items = uiState.todayEntries, key = { "t-${it.id}" }) { entry ->
                WorkEntryRow(
                    entry = entry,
                    onAdvanceStatus = { onAdvance(entry) },
                    onClick = { onEntryClick(entry.id) },
                    // Every row here is today's, so the date would just repeat.
                    showDate = false,
                    // Completed work is actionable in the band above, not here.
                    showAction = entry.status == Status.IN_PROGRESS,
                    modifier = Modifier
                        .animateItem()
                        .then(rowPadding)
                        .padding(vertical = TrackoraSpacing.xs)
                )
            }
            item(key = "today-summary") {
                // The counts that used to occupy two large tiles, reduced to one
                // quiet line underneath the work they describe.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = TrackoraSpacing.screen, vertical = TrackoraSpacing.md)
                ) {
                    Text(
                        text = stringResource(
                            id = R.string.today_summary_line,
                            uiState.completedToday,
                            uiState.quantityToday
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = TrackoraSpacing.md, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
