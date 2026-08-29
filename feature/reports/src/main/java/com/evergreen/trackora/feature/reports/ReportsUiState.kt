package com.evergreen.trackora.feature.reports

/**
 * Figures for one reporting window.
 *
 * @param completed finished work in the window, whether or not it has been handed over.
 * @param totalQuantity units produced in the window — the number a pieceworker bills on.
 * @param previousCompleted the same count for the immediately preceding window of
 *   equal length, so the screen can say whether this period is better or worse.
 *   A bare total answers "how much"; only the comparison answers "how am I doing".
 */
data class ReportSummary(
    val completed: Int = 0,
    val totalQuantity: Int = 0,
    val previousCompleted: Int = 0
) {
    /** Signed change against the previous window, or null when there is nothing to compare. */
    val changeVsPrevious: Int?
        get() = if (previousCompleted == 0 && completed == 0) null else completed - previousCompleted

    val hasActivity: Boolean
        get() = completed > 0 || totalQuantity > 0
}

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
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
