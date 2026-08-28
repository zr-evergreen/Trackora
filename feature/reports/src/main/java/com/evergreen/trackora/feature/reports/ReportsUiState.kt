package com.evergreen.trackora.feature.reports

/**
 * Simple numeric summaries for reports.
 */
data class ReportSummary(
    val completed: Int = 0,
    val delivered: Int = 0,
    val totalQuantity: Int = 0
)

data class ReportsUiState(
    val daily: ReportSummary = ReportSummary(),
    val weekly: ReportSummary = ReportSummary(),
    val monthly: ReportSummary = ReportSummary(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)


