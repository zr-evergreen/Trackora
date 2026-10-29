package com.evergreen.trackora.feature.reports

import java.time.LocalDate

/**
 * Figures for one reporting window.
 *
 * @param completed finished work in the window, whether or not it has been handed over.
 * @param totalQuantity units produced in the window — the number a pieceworker bills on.
 * @param previousCompleted the same count for the immediately preceding window of
 *   equal length, so the screen can say whether this period is better or worse.
 *   A bare total answers "how much"; only the comparison answers "how am I doing".
 * @param previousQuantity units in that preceding window, for the same comparison
 *   on the figure a pieceworker is paid on.
 * @param days every day of the window, oldest first, including days with nothing
 *   recorded — a gap in the chart is information, and leaving the day out would
 *   draw a busy Monday next to a busy Wednesday as if Tuesday had not happened.
 * @param previousDays the same for the preceding window.
 * @param inProgress work recorded in the window that is still being done.
 * @param waiting work recorded in the window that is finished and not yet delivered.
 * @param delivered work recorded in the window that has reached its customer.
 *   [waiting] plus [delivered] is [completed], by the same definition.
 */
data class ReportSummary(
    val completed: Int = 0,
    val totalQuantity: Int = 0,
    val previousCompleted: Int = 0,
    val previousQuantity: Int = 0,
    val days: List<DayFigures> = emptyList(),
    val previousDays: List<DayFigures> = emptyList(),
    val inProgress: Int = 0,
    val waiting: Int = 0,
    val delivered: Int = 0,
) {
    /** Signed change against the previous window, or null when there is nothing to compare. */
    val changeVsPrevious: Int?
        get() = if (previousCompleted == 0 && completed == 0) null else completed - previousCompleted

    /** Signed change in units against the previous window, or null when there is nothing to compare. */
    val quantityChangeVsPrevious: Int?
        get() = if (previousQuantity == 0 && totalQuantity == 0) null else totalQuantity - previousQuantity

    /** Every entry recorded in the window, whatever its status. */
    val recorded: Int
        get() = inProgress + waiting + delivered

    /**
     * Whether anything was recorded in the window. Work still in progress
     * counts: three jobs started today are activity even before any is done.
     */
    val hasActivity: Boolean
        get() = completed > 0 || totalQuantity > 0 || inProgress > 0
}

/** One day's figures within a window. */
data class DayFigures(
    val date: LocalDate,
    /** Work recorded on [date] that is finished, delivered or not. */
    val finished: Int,
    /** Units recorded on [date], whatever their status. */
    val units: Int,
)

data class ReportsUiState(
    val daily: ReportSummary = ReportSummary(),
    val weekly: ReportSummary = ReportSummary(),
    val monthly: ReportSummary = ReportSummary(),
    /**
     * Work finished but not handed over, right now.
     *
     * Deliberately not scoped to the selected range: "what do I still owe
     * customers" is a question about the present, not about a window. A job
     * completed two months ago and still uncollected must not vanish from this
     * figure because the user is looking at a weekly report.
     */
    val undeliveredCount: Int = 0,
    /** The date of the longest-waiting undelivered job, or null when nothing is waiting. */
    val oldestWaiting: LocalDate? = null,
    /** The day the windows end on, so the screen labels the same days it was given. */
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
