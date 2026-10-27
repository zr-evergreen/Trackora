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
     * The filtered list grouped into days, newest first.
     *
     * History reads as days, not as an undifferentiated stream. Grouping also
     * removes the repetition of printing the same date on every row of a busy
     * day — with a header the row only needs to say what makes it different.
     */
    val sections: List<DaySection>
        get() = filteredEntries
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, entries) -> DaySection(date = date, entries = entries) }

    val isSearching: Boolean
        get() = query.isNotBlank()

    /** True when a filter or search is hiding everything, as opposed to an empty log. */
    val hasNoMatches: Boolean
        get() = entries.isNotEmpty() && filteredEntries.isEmpty()
}

data class DaySection(
    val date: LocalDate,
    val entries: List<WorkEntry>
)

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
