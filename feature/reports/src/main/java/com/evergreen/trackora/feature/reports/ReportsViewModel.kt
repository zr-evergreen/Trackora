package com.evergreen.trackora.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateRangeUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateUseCase
import com.evergreen.trackora.util.AppConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for numeric reports.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getWorkEntriesByDateUseCase: GetWorkEntriesByDateUseCase,
    private val getWorkEntriesByDateRangeUseCase: GetWorkEntriesByDateRangeUseCase
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    init {
        loadReports()
    }

    private fun loadReports() {
        viewModelScope.launch {
            combine(
                getWorkEntriesByDateUseCase(today).map { entries ->
                    toSummary(entries = entries)
                },
                getWorkEntriesByDateRangeUseCase(today.minusDays(6), today).map { entries ->
                    toSummary(entries = entries)
                },
                getWorkEntriesByDateRangeUseCase(today.minusDays(29), today).map { entries ->
                    toSummary(entries = entries)
                }
            ) { daily, weekly, monthly ->
                ReportsUiState(
                    daily = daily,
                    weekly = weekly,
                    monthly = monthly,
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
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    /**
     * Converts a list of work entries into a summary report.
     * Extracted to a separate method for better testability and Single Responsibility.
     */
    private fun toSummary(entries: List<WorkEntry>): ReportSummary {
        val completed = entries.count { it.status == Status.COMPLETED }
        val delivered = entries.count { it.status == Status.DELIVERED }
        val totalQuantity = entries.sumOf { it.quantity ?: 0 }
        return ReportSummary(
            completed = completed,
            delivered = delivered,
            totalQuantity = totalQuantity
        )
    }
}


