package com.evergreen.trackora.feature.allwork

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.util.PersianSearch
import java.time.LocalDate

/**
 * UI state for the All Work screen.
 *
 * Filtering and grouping are derived here rather than stored, so there is one
 * source of truth — the entry list — and no way for a cached result to drift
 * out of step with it after an edit.
 */
data class AllWorkUiState(
    val entries: List<WorkEntry> = emptyList(),
    val filter: Status? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Held only while the undo snackbar is on screen. */
    val recentlyDeleted: WorkEntry? = null,
    /** The pre-change entry, held only while the undo snackbar is on screen. */
    val recentlyAdvanced: WorkEntry? = null
) {
    val filteredEntries: List<WorkEntry>
        get() = entries
            .filter { filter == null || it.status == filter }
            .filter { it.matches(query) }

    /**
     * How many entries match the search, per status: what each status filter
     * would show if chosen, so the filters say what is behind them before
     * they are tapped. Statuses with nothing are absent.
     */
    val countsByStatus: Map<Status, Int>
        get() = entries.filter { it.matches(query) }.groupingBy { it.status }.eachCount()

    /** How many entries match the search whatever their status: what "All" would show. */
    val matchingCount: Int
        get() = entries.count { it.matches(query) }

    /**
     * The filtered list as the screen lays it out, newest first.
     *
     * History reads as days, not as an undifferentiated stream — but only
     * while the days are recent enough to be remembered as days. The last
     * [RECENT_DAYS] each get their own section; anything older is gathered
     * by calendar month, where a heading for every quiet Tuesday of last
     * spring would otherwise be most of what was on screen.
     *
     * @param monthKeyOf names the month a date falls in, in the calendar the
     *   user reads — Jalali months for Persian, whose boundaries fall mid-way
     *   through Gregorian ones.
     */
    fun sections(today: LocalDate, monthKeyOf: (LocalDate) -> Int): List<WorkSection> {
        val firstRecentDay = today.minusDays(RECENT_DAYS - 1L)
        val (recent, older) = filteredEntries
            .sortedByDescending { it.date }
            .partition { !it.date.isBefore(firstRecentDay) }
        val days = recent.groupBy { it.date }
            .map { (date, entries) -> WorkSection.Day(date = date, entries = entries) }
        val months = older.groupBy { monthKeyOf(it.date) }
            .map { (key, entries) -> WorkSection.Month(monthKey = key, latest = entries.first().date, entries = entries) }
        return days + months
    }

    val isSearching: Boolean
        get() = query.isNotBlank()

    /** Whether a status filter or a search is narrowing the list. */
    val isNarrowed: Boolean
        get() = filter != null || isSearching

    /** True when a filter or search is hiding everything, as opposed to an empty log. */
    val hasNoMatches: Boolean
        get() = entries.isNotEmpty() && filteredEntries.isEmpty()

    companion object {
        /** Days, counting today, that keep a section of their own. */
        const val RECENT_DAYS = 7
    }
}

/** A run of entries under one heading on All Work. */
sealed interface WorkSection {
    val entries: List<WorkEntry>

    /** Stable across recompositions, for the list's keys. */
    val key: String

    /** Items across the section's entries, for its heading. */
    val items: Int
        get() = entries.sumOf { it.quantity ?: 0 }

    /** One recent day. */
    data class Day(val date: LocalDate, override val entries: List<WorkEntry>) : WorkSection {
        override val key: String get() = "day-$date"
    }

    /**
     * One older calendar month. [latest] is its most recent date, which the
     * heading names the month by.
     */
    data class Month(val monthKey: Int, val latest: LocalDate, override val entries: List<WorkEntry>) : WorkSection {
        override val key: String get() = "month-$monthKey"
    }
}

/**
 * Whether this entry matches a search [query].
 *
 * Searches the text the user actually typed — title, description and the three
 * custom fields, which in practice hold the customer name and job code — plus
 * the quantity, so someone can find an order by the number of pieces.
 *
 * Status and date are deliberately not searched as text. They are already
 * filterable and grouped respectively, and matching them as strings would mean
 * a query like "completed" behaving differently in each language.
 */
private fun WorkEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return PersianSearch.matches(title, query) ||
        PersianSearch.matches(description, query) ||
        PersianSearch.matches(customField1, query) ||
        PersianSearch.matches(customField2, query) ||
        PersianSearch.matches(customField3, query) ||
        PersianSearch.matches(quantity?.toString(), query)
}
