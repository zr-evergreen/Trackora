package com.evergreen.trackora.feature.allwork

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraFilterChip
import com.evergreen.trackora.ui.components.TrackoraFilterRow
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.TrackoraScreenContainer
import com.evergreen.trackora.ui.components.WorkEntryRow
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.text.localizedNumber
import java.time.LocalDate
import com.evergreen.trackora.ui.text.forUserContent

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
    val snackbarHostState = remember { SnackbarHostState() }
    // The search field is the first focusable element here, so on the first
    // navigation to this screen Compose hands it initial focus and the keyboard
    // covers half the list before the user has asked to search anything.
    // Parking focus on the container instead leaves the field unfocused until
    // it is actually tapped. clearFocus() does not work for this — it runs
    // before the field claims focus, not after.
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(initialFocus)
            .focusable()
    ) {
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

        SearchField(
            query = uiState.query,
            onQueryChange = viewModel::setQuery,
            onClear = viewModel::clearQuery
        )

        Spacer(modifier = Modifier.height(12.dp))

        StatusFilterRow(
            selected = uiState.filter,
            onFilterSelected = viewModel::setFilter
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> TrackoraLoadingState()

            uiState.errorMessage != null -> TrackoraErrorState(
                title = stringResource(id = com.evergreen.trackora.common.R.string.state_error_title),
                message = stringResource(id = com.evergreen.trackora.common.R.string.state_error_body),
                retryLabel = stringResource(id = com.evergreen.trackora.common.R.string.state_retry),
                onRetry = viewModel::retry
            )

            // A search that matched nothing is a different situation from an
            // empty log, and telling them apart is the difference between
            // "try another word" and "add your first job".
            uiState.hasNoMatches -> TrackoraEmptyState(
                title = stringResource(id = R.string.search_no_results_title),
                body = stringResource(id = R.string.search_no_results_body)
            )

            uiState.filteredEntries.isEmpty() -> TrackoraEmptyState(
                title = stringResource(id = R.string.empty_all_work)
            )

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    uiState.sections.forEach { section ->
                        item(key = "h-${section.date}") {
                            DayHeader(date = section.date, count = section.entries.size)
                        }
                        items(items = section.entries, key = { it.id }) { entry ->
                            SwipeableEntry(
                                entry = entry,
                                onClick = { onEntryClick(entry.id) },
                                onAdvanceStatus = { viewModel.advanceStatus(entry) },
                                onDelete = { viewModel.deleteEntry(entry) }
                            )
                        }
                    }
                }
            }
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
 */
@Composable
private fun SwipeableEntry(
    entry: WorkEntry,
    onClick: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onDelete: () -> Unit
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
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Must match WorkEntryRow's own card padding exactly, or
                    // the background shows as a permanent coloured sliver down
                    // both edges of every row instead of appearing on swipe.
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.delete_entry),
                    tint = MaterialTheme.colorScheme.onErrorContainer
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
    val filters: List<Pair<String, Status?>> = listOf(
        stringResource(id = R.string.filter_all) to null,
        stringResource(id = R.string.filter_in_progress) to Status.IN_PROGRESS,
        stringResource(id = R.string.filter_completed) to Status.COMPLETED,
        stringResource(id = R.string.filter_delivered) to Status.DELIVERED
    )

    TrackoraFilterRow {
        filters.forEach { (label, status) ->
            TrackoraFilterChip(
                label = label,
                selected = selected == status,
                onClick = { onFilterSelected(status) }
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
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(text = stringResource(id = R.string.search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            // Only present when there is something to clear, so the field is
            // not permanently carrying a dead control.
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
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
        shape = MaterialTheme.shapes.medium
    )
}

/** Date heading for one day's work, with how many entries it holds. */
@Composable
private fun DayHeader(date: LocalDate, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localizedDate(date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = localizedNumber(count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
