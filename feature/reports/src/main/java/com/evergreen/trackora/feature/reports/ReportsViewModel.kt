package com.evergreen.trackora.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetUndeliveredWorkUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateRangeUseCase
import com.evergreen.trackora.util.AppConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for numeric reports.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getWorkEntriesByDateRangeUseCase: GetWorkEntriesByDateRangeUseCase,
    private val getUndeliveredWorkUseCase: GetUndeliveredWorkUseCase
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    /** Re-runs the report queries after a failure. */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadReports()
    }

    private fun loadReports() {
        viewModelScope.launch {
            // Each window is paired with the equal-length window before it, so
            // the screen can report a direction of travel rather than a bare
            // total. Yesterday for today, the previous seven days for the last
            // seven, and so on.
            combine(
                window(today, today, today.minusDays(1), today.minusDays(1)),
                window(today.minusDays(6), today, today.minusDays(13), today.minusDays(7)),
                window(today.minusDays(29), today, today.minusDays(59), today.minusDays(30)),
                getUndeliveredWorkUseCase()
            ) { daily, weekly, monthly, undelivered ->
                ReportsUiState(
                    daily = daily,
                    weekly = weekly,
                    monthly = monthly,
                    undeliveredCount = undelivered.size,
                    isLoading = false
                )
            }
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.message
                            ?: AppConstants.Errors.FAILED_TO_LOAD_ENTRIES
                    )
                }
                .collect { state -> _uiState.value = state }
        }
    }

    /** Figures for [start]..[end], compared against [prevStart]..[prevEnd]. */
    private fun window(
        start: LocalDate,
        end: LocalDate,
        prevStart: LocalDate,
        prevEnd: LocalDate
    ) = combine(
        getWorkEntriesByDateRangeUseCase(start, end),
        getWorkEntriesByDateRangeUseCase(prevStart, prevEnd)
    ) { current, previous ->
        toSummary(current).copy(
            previousCompleted = previous.count { it.status != Status.IN_PROGRESS }
        )
    }

    /**
     * Completed counts finished work whether or not it has been handed over —
     * delivering something does not un-complete it, and a report that dropped
     * delivered work would show a user's output falling as they hand it over.
     */
    private fun toSummary(entries: List<WorkEntry>): ReportSummary {
        return ReportSummary(
            completed = entries.count { it.status != Status.IN_PROGRESS },
            totalQuantity = entries.sumOf { it.quantity ?: 0 }
        )
    }
}
