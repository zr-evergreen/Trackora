package com.evergreen.trackora.feature.allwork

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R as CommonR
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.CountPill
import com.evergreen.trackora.ui.components.ScreenHeader
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraFilterChip
import com.evergreen.trackora.ui.components.TrackoraFilterRow
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.WorkEntryRow
import com.evergreen.trackora.ui.components.trackoraTextFieldColors
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.ui.theme.container
import com.evergreen.trackora.ui.theme.content
import com.evergreen.trackora.ui.theme.statusColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Screen for viewing all work entries with quick status filters.
 *
 * The history reads as a ledger: one heading per day that stays pinned while
 * that day's entries scroll beneath it, so a long list never loses track of
 * which day the user is looking at.
 */
@Composable
fun AllWorkScreen(
    contentPadding: PaddingValues,
    onEntryClick: (Long) -> Unit,
    viewModel: AllWorkViewModel,
    initialStatus: Status? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Applied once per arrival, not on every recomposition, so the user can
    // still clear the filter on a screen they were sent to with one set.
    // Saveable, because opening an entry and coming back re-enters
    // composition, and an unguarded effect would re-apply the filter over
    // whatever the user had chosen since.
    var initialStatusApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialStatus) {
        if (initialStatus != null && !initialStatusApplied) {
            viewModel.setFilter(initialStatus)
            initialStatusApplied = true
        }
    }

    val deletedTitle = uiState.recentlyDeleted?.title
    val deletedMessage = deletedTitle?.let { stringResource(id = R.string.entry_deleted, it) }
    val undoLabel = stringResource(id = R.string.undo)

    // Shown once per deletion. Dismissing without tapping Undo drops the held
    // entry, so a stale one cannot be restored by a later snackbar.
    LaunchedEffect(uiState.recentlyDeleted) {
        if (deletedMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            else viewModel.clearRecentlyDeleted()
        }
    }

    val advancedTitle = uiState.recentlyAdvanced?.title
    val advancedMessage = advancedTitle?.let {
        stringResource(id = R.string.entry_status_changed, it)
    }

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
            val showCount = !uiState.isLoading && uiState.errorMessage == null &&
                uiState.filteredEntries.isNotEmpty()
            ScreenHeader(
                title = stringResource(id = R.string.all_work_title),
                // How many entries the current search and filter leave, so
                // narrowing the list says what it did.
                subtitle = if (showCount) {
                    pluralStringResource(
                        id = R.plurals.all_work_count,
                        count = uiState.filteredEntries.size,
                        uiState.filteredEntries.size
                    )
                } else {
                    null
                }
            )

            SearchField(
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                onClear = viewModel::clearQuery,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )

            Spacer(modifier = Modifier.height(TrackoraSpacing.md))

            StatusFilterRow(
                selected = uiState.filter,
                onFilterSelected = viewModel::setFilter
            )

            Spacer(modifier = Modifier.height(TrackoraSpacing.xs))

            when {
                uiState.isLoading -> TrackoraLoadingState()

                uiState.errorMessage != null -> TrackoraErrorState(
                    title = stringResource(id = CommonR.string.state_error_title),
                    message = stringResource(id = CommonR.string.state_error_body),
                    retryLabel = stringResource(id = CommonR.string.state_retry),
                    onRetry = viewModel::retry
                )

                // A search that matched nothing is a different situation from an
                // empty log, and telling them apart is the difference between
                // "try another word" and "add your first job".
                uiState.hasNoMatches -> TrackoraEmptyState(
                    title = stringResource(id = R.string.search_no_results_title),
                    body = stringResource(id = R.string.search_no_results_body),
                    icon = TrackoraIcons.NoResults
                )

                uiState.filteredEntries.isEmpty() -> TrackoraEmptyState(
                    title = stringResource(id = R.string.empty_all_work),
                    icon = TrackoraIcons.HistoryOutlined
                )

                else -> EntryList(
                    uiState = uiState,
                    onEntryClick = onEntryClick,
                    onAdvanceStatus = viewModel::advanceStatus,
                    onDelete = viewModel::deleteEntry
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryList(
    uiState: AllWorkUiState,
    onEntryClick: (Long) -> Unit,
    onAdvanceStatus: (WorkEntry) -> Unit,
    onDelete: (WorkEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    // Same reason as on Today: a lazy list holds its first visible item in
    // place by key, so undoing the delete of the newest day's only entry
    // restored its section above the viewport and Undo looked as if it had
    // done nothing. A list at the top stays at the top; one scrolled down
    // keeps its place.
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }
    if (atTop) listState.requestScrollToItem(0)
    val today = LocalDate.now()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TrackoraSpacing.xxl)
    ) {
        uiState.sections.forEach { section ->
            stickyHeader(key = "h-${section.date}") {
                DayHeader(date = section.date, today = today, count = section.entries.size)
            }
            items(items = section.entries, key = { it.id }) { entry ->
                SwipeableEntry(
                    entry = entry,
                    onClick = { onEntryClick(entry.id) },
                    onAdvanceStatus = { onAdvanceStatus(entry) },
                    onDelete = { onDelete(entry) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/**
 * An entry row that can be swiped away to delete.
 *
 * Swipe is the only delete affordance because the alternative — a menu on
 * every row — adds permanent visual weight for an action used rarely. It is
 * safe to make it this easy only because the deletion is undoable; the two
 * ship together deliberately.
 *
 * Both directions delete. Reserving one direction for a second action would
 * mean the gesture does different things depending on which way the user
 * happens to swipe, which in an app that flips between LTR and RTL is a
 * reliable way to delete something by accident.
 *
 * The bin sits on the side the card uncovers, and the tray beneath turns red
 * and the bin grows once the swipe has gone far enough to count — so the
 * point of no return is visible before the finger lifts.
 */
@Composable
private fun SwipeableEntry(
    entry: WorkEntry,
    onClick: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        // The card's inset is applied to the whole box, so the tray behind
        // it is exactly the card's size and never shows as a sliver at rest.
        modifier = modifier.padding(horizontal = TrackoraSpacing.screen, vertical = TrackoraSpacing.xs),
        backgroundContent = {
            val armed = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val trayColor by animateColorAsState(
                targetValue = if (armed) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = tween(TrackoraMotion.SHORT),
                label = "deleteTray"
            )
            val binScale by animateFloatAsState(
                targetValue = if (armed) 1.15f else 0.9f,
                animationSpec = tween(TrackoraMotion.SHORT),
                label = "deleteBin"
            )
            val binColor = if (armed) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(trayColor)
                    .padding(horizontal = TrackoraSpacing.xxl),
                // Start and End follow the layout direction, so the bin is on
                // the uncovered side in Persian as well as English.
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                Icon(
                    imageVector = TrackoraIcons.Delete,
                    contentDescription = stringResource(id = R.string.delete_entry),
                    tint = binColor,
                    modifier = Modifier.scale(binScale)
                )
            }
        }
    ) {
        WorkEntryRow(
            entry = entry,
            onClick = onClick,
            onAdvanceStatus = onAdvanceStatus,
            // The day header above carries the date now, so repeating it on
            // every row of a busy day would be noise.
            showDate = false
        )
    }
}

@Composable
private fun StatusFilterRow(
    selected: Status?,
    onFilterSelected: (Status?) -> Unit
) {
    val statusColors = MaterialTheme.statusColors
    TrackoraFilterRow(contentPadding = PaddingValues(horizontal = TrackoraSpacing.screen)) {
        TrackoraFilterChip(
            label = stringResource(id = R.string.filter_all),
            selected = selected == null,
            onClick = { onFilterSelected(null) }
        )
        listOf(
            Status.IN_PROGRESS to R.string.filter_in_progress,
            Status.COMPLETED to R.string.filter_completed,
            Status.DELIVERED to R.string.filter_delivered
        ).forEach { (status, label) ->
            TrackoraFilterChip(
                label = stringResource(id = label),
                selected = selected == status,
                onClick = { onFilterSelected(status) },
                icon = TrackoraIcons.forStatus(status),
                selectedContainerColor = statusColors.container(status),
                selectedContentColor = statusColors.content(status)
            )
        }
    }
}

/**
 * The search box.
 *
 * Folded through [com.evergreen.trackora.util.PersianSearch], so a query typed
 * with an Arabic keyboard still finds text stored with Persian letterforms —
 * the two are visually identical and a plain contains() would simply return
 * nothing.
 *
 * Search runs as the user types. The list is already in memory and one
 * person's work log is small enough that filtering it is imperceptible, so a
 * submit button would add a step for nothing.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(text = stringResource(id = R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = TrackoraIcons.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            // Only present when there is something to clear, so the field is
            // not permanently carrying a dead control.
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = TrackoraIcons.Clear,
                        contentDescription = stringResource(id = R.string.search_clear)
                    )
                }
            }
        },
        singleLine = true,
        // The query is text the user typed and may be in either script, so it
        // resolves its own direction rather than inheriting the layout's.
        textStyle = LocalTextStyle.current.forUserContent(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = MaterialTheme.shapes.extraLarge,
        colors = trackoraTextFieldColors(container = MaterialTheme.colorScheme.cardContainer)
    )
}

/**
 * Date heading for one day's work, with how many entries it holds.
 *
 * Pinned while its day scrolls, so it has the page's own background to hide
 * the cards passing under it. The two most recent days read as words, with
 * the date beside them, because "Yesterday" is what the user is looking for.
 */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, count: Int) {
    val relative = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> stringResource(id = CommonR.string.date_today)
        1L -> stringResource(id = CommonR.string.date_yesterday)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = TrackoraSpacing.screen, end = TrackoraSpacing.screen, top = 14.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = relative ?: localizedDate(date, includeWeekday = true),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (relative != null) {
            Text(
                text = localizedDate(date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        CountPill(count = count, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
