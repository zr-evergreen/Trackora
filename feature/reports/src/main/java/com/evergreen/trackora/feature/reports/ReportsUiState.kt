package com.evergreen.trackora.feature.reports

/**
 * Simple numeric summaries for reports.
 */
data class ReportSummary(
    val label: String,
    val completed: Int = 0,
    val delivered: Int = 0,
    val totalQuantity: Int = 0
)

data class ReportsUiState(
    val daily: ReportSummary = ReportSummary(label = "Today"),
    val weekly: ReportSummary = ReportSummary(label = "Last 7 days"),
    val monthly: ReportSummary = ReportSummary(label = "Last 30 days"),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)


