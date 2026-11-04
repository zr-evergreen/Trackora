package com.evergreen.trackora.feature.allwork

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R as CommonR
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.components.ScreenHeader
import com.evergreen.trackora.ui.components.TrackoraEmptyState
import com.evergreen.trackora.ui.components.TrackoraErrorState
import com.evergreen.trackora.ui.components.TrackoraLoadingState
import com.evergreen.trackora.ui.components.WorkEntryRow
import com.evergreen.trackora.ui.components.trackoraTextFieldColors
import com.evergreen.trackora.ui.text.calendarMonthKey
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.isPersianLocale
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.text.localizedMonthTitle
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Every job ever recorded, built for the two things people come here to do:
 * find a particular job, and move work along.
 *
 * ### Layout
 *
 * The title and its summary — how many entries, how many items, and how many
 * of them the current search and filter leave — scroll away as the list is
 * read, while search and the status filters stay pinned above it. On a small
 * phone the header used to take a third of the screen permanently.
 *
 * ### Finding
 *
 * The status filters are an overview of the list — all four always in view,
 * each led by how many entries it holds within the current search — so the
 * effect of a tap is visible before it is made; see [StatusOverview]. Matches are
 * highlighted in each card, and a match in a customer name or job code is
 * shown on the card, so every result says why it is there. When a search
 * or filter leaves nothing, one button clears both.
 *
 * ### Grouping
 *
 * The last week reads day by day, each day pinned while its entries scroll
 * beneath it; older work is gathered by month — Jalali months in Persian —
 * with each card carrying its own date. Every heading totals its entries
 * and items.
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

    // The header gives way to the list as it scrolls up and returns once the
    // list is back at its top.
    val collapse = remember { HeaderCollapse() }
    val listState = rememberLazyListState()
    val listScrolled by remember { derivedStateOf { listState.canScrollBackward } }
    // A search or filter can shrink the list until it fits on screen, and a
    // list that cannot scroll can never pull the header back. Nothing needs
    // the room then, so the header returns by itself.
    LaunchedEffect(listState, collapse) {
        snapshotFlow { !listState.canScrollForward && !listState.canScrollBackward }
            .collect { fits -> if (fits) collapse.settle(open = true) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .nestedScroll(collapse.connection)
        ) {
            CollapsingHeader(state = collapse) {
                ScreenHeader(
                    title = stringResource(id = R.string.all_work_title),
                    subtitle = summaryLine(uiState)
                )
            }

            SearchField(
                query = uiState.query,
                onQueryChange = viewModel::setQuery,
                onClear = viewModel::clearQuery,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )
            Spacer(modifier = Modifier.height(TrackoraSpacing.sm))
            StatusOverview(
                selected = uiState.filter,
                allCount = uiState.matchingCount,
                counts = uiState.countsByStatus,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.screen)
            )
            // A hairline under the pinned controls once cards pass beneath them.
            val edge by animateFloatAsState(
                targetValue = if (listScrolled) 1f else 0f,
                animationSpec = tween(TrackoraMotion.SHORT),
                label = "controlsEdge"
            )
            HorizontalDivider(
                modifier = Modifier
                    .padding(top = TrackoraSpacing.sm)
                    .graphicsLayer { alpha = edge },
                color = MaterialTheme.colorScheme.outlineVariant
            )

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
                    icon = TrackoraIcons.NoResults,
                    actionLabel = stringResource(id = R.string.all_work_show_all),
                    onAction = {
                        viewModel.clearQuery()
                        viewModel.setFilter(null)
                    }
                )

                uiState.filteredEntries.isEmpty() -> TrackoraEmptyState(
                    title = stringResource(id = R.string.empty_all_work),
                    icon = TrackoraIcons.HistoryOutlined
                )

                else -> EntryList(
                    uiState = uiState,
                    listState = listState,
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

/**
 * "11 entries · 46 items", or "4 of 11 entries · 12 items" while a search or
 * filter narrows the list, so narrowing says what it did.
 */
@Composable
private fun summaryLine(uiState: AllWorkUiState): String? {
    if (uiState.isLoading || uiState.errorMessage != null || uiState.entries.isEmpty()) return null
    val total = uiState.entries.size
    val shown = uiState.filteredEntries.size
    val entries = if (uiState.isNarrowed) {
        pluralStringResource(R.plurals.all_work_count_of, total, shown, total)
    } else {
        pluralStringResource(R.plurals.all_work_count, total, total)
    }
    val items = uiState.filteredEntries.sumOf { it.quantity ?: 0 }
    return if (items > 0) {
        stringResource(R.string.all_work_summary, entries, pluralStringResource(R.plurals.all_work_items, items, items))
    } else {
        entries
    }
}

/**
 * How far the header has given way to the list: [offset] runs from 0, fully
 * shown, to minus [limit], fully hidden.
 *
 * Its own nested-scroll connection rather than Material's top-app-bar
 * behaviour, which consumes the whole scroll whenever the bar moves at all
 * and is rebuilt on recomposition. Here the header takes exactly the pixels
 * it uses and the list keeps the rest, so the two never fight over a drag.
 * Upward scrolling hides the header before the list moves; downward
 * scrolling brings it back only once the list has reached its top. A fling
 * that leaves it half open settles it to whichever end is nearer.
 */
@Stable
internal class HeaderCollapse {
    var offset by mutableFloatStateOf(0f)
        private set
    var limit by mutableFloatStateOf(0f)

    /** 0 when fully shown, 1 when fully hidden. */
    val fraction: Float
        get() = if (limit == 0f) 0f else -offset / limit

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0f) Offset(0f, take(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (available.y > 0f) Offset(0f, take(available.y)) else Offset.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle(open = fraction < 0.5f)
            return Velocity.Zero
        }
    }

    /** Moves the header by up to [delta] pixels and returns how many it used. */
    private fun take(delta: Float): Float {
        val next = (offset + delta).coerceIn(-limit, 0f)
        val used = next - offset
        offset = next
        return used
    }

    /** Animates the header fully open or fully closed, if it is not already. */
    suspend fun settle(open: Boolean) {
        val target = if (open) 0f else -limit
        if (offset == target) return
        animate(offset, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
            offset = value
        }
    }
}

/**
 * Shows [content] at its full height, then gives up height to the list as
 * [state] collapses, sliding up and fading as it goes.
 */
@Composable
internal fun CollapsingHeader(
    state: HeaderCollapse,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val visible = (placeable.height + state.offset).roundToInt().coerceIn(0, placeable.height)
                layout(placeable.width, visible) {
                    placeable.place(0, visible - placeable.height)
                }
            }
    ) {
        Box(
            modifier = Modifier
                // The header's full height is how far it can give way. Set
                // where it is measured: read only inside an effect, a height
                // never recomposes anything, and the limit would stay at zero.
                .onSizeChanged { state.limit = it.height.toFloat() }
                .graphicsLayer { alpha = 1f - state.fraction }
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryList(
    uiState: AllWorkUiState,
    listState: LazyListState,
    onEntryClick: (Long) -> Unit,
    onAdvanceStatus: (WorkEntry) -> Unit,
    onDelete: (WorkEntry) -> Unit,
) {
    // Same reason as on Today: a lazy list holds its first visible item in
    // place by key, so undoing the delete of the newest day's only entry
    // restored its section above the viewport and Undo looked as if it had
    // done nothing. A list at the top stays at the top; one scrolled down
    // keeps its place.
    //
    // Only when the entries, filter or search actually change, though: the
    // request cancels any scroll in progress, and made on every recomposition
    // it cancelled the user's own drag the moment the collapsing header
    // recomposed this list, pinning it to its first card.
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        }
    }
    val anchor = remember { ListAnchor() }
    if (anchor.changed(uiState.entries, uiState.filter, uiState.query) && atTop) {
        listState.requestScrollToItem(0)
    }
    val today = LocalDate.now()
    val jalali = isPersianLocale()
    val sections = uiState.sections(today) { calendarMonthKey(it, jalali) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TrackoraSpacing.xxl)
    ) {
        sections.forEach { section ->
            stickyHeader(key = section.key) {
                SectionHeading(section = section, today = today)
            }
            items(items = section.entries, key = { it.id }) { entry ->
                SwipeableEntry(
                    entry = entry,
                    // Older work is grouped by month, so its cards carry their own day.
                    showDate = section is WorkSection.Month,
                    searchQuery = uiState.query,
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
 * Remembers what the list last showed, to tell a change in the data from a
 * recomposition that changed nothing. Plain fields rather than state: it is
 * read and updated within one composition and must not trigger another.
 */
private class ListAnchor {
    private var entries: List<WorkEntry>? = null
    private var filter: Status? = null
    private var query: String? = null

    /** Whether any of these differ from the last call, recording them either way. */
    fun changed(entries: List<WorkEntry>, filter: Status?, query: String): Boolean {
        val changed = entries !== this.entries || filter != this.filter || query != this.query
        this.entries = entries
        this.filter = filter
        this.query = query
        return changed
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
    showDate: Boolean,
    searchQuery: String,
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
            showDate = showDate,
            searchQuery = searchQuery
        )
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
 * The heading of a day or a month, pinned while its entries scroll beneath
 * it, so it has the page's own background to hide the cards passing under.
 *
 * A day reads "Today" or "Yesterday" for the two most recent, with the date
 * beside them, and its full weekday and date otherwise; a month reads by its
 * name. Either way the heading ends with what it holds: entries, and items
 * when there are any.
 */
@Composable
private fun SectionHeading(section: WorkSection, today: LocalDate) {
    val title: String
    val detail: String?
    when (section) {
        is WorkSection.Day -> {
            val relative = when (ChronoUnit.DAYS.between(section.date, today)) {
                0L -> stringResource(id = CommonR.string.date_today)
                1L -> stringResource(id = CommonR.string.date_yesterday)
                else -> null
            }
            title = relative ?: localizedDate(section.date, includeWeekday = true)
            detail = if (relative != null) localizedDate(section.date) else null
        }
        is WorkSection.Month -> {
            title = localizedMonthTitle(section.latest)
            detail = null
        }
    }
    val count = section.entries.size
    val entries = pluralStringResource(R.plurals.all_work_count, count, count)
    val totals = if (section.items > 0) {
        stringResource(
            R.string.all_work_summary,
            entries,
            pluralStringResource(R.plurals.all_work_items, section.items, section.items)
        )
    } else {
        entries
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
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = totals,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
