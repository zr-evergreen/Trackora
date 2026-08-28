package com.evergreen.trackora.feature.today

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry

/**
 * State for the Today screen.
 *
 * The screen answers two questions in priority order, and the state is shaped
 * to match: what is finished and still owed to a customer, then what happened
 * today. [undelivered] is not restricted to today — that is the point of it.
 */
data class TodayUiState(
    /** Completed but not delivered, any date, oldest first. */
    val undelivered: List<WorkEntry> = emptyList(),
    val todayEntries: List<WorkEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** The entry as it was before its last status change, held while the undo snackbar shows. */
    val recentlyAdvanced: WorkEntry? = null,
) {
    /** True only when there is nothing to show anywhere on the screen. */
    val isEmpty: Boolean
        get() = todayEntries.isEmpty() && undelivered.isEmpty() && !isLoading

    val undeliveredCount: Int get() = undelivered.size

    /** Finished today — the number a user checks at closing time. */
    val completedToday: Int
        get() = todayEntries.count { it.status == Status.COMPLETED || it.status == Status.DELIVERED }

    /** Units produced today, ignoring entries that carry no quantity. */
    val quantityToday: Int
        get() = todayEntries.sumOf { it.quantity ?: 0 }
}
