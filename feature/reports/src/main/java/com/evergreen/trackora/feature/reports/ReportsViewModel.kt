package com.evergreen.trackora.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateRangeUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateUseCase
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
                    toSummary(label = "Today", entries = entries)
                },
                getWorkEntriesByDateRangeUseCase(today.minusDays(6), today).map { entries ->
                    toSummary(label = "Last 7 days", entries = entries)
                },
                getWorkEntriesByDateRangeUseCase(today.minusDays(29), today).map { entries ->
                    toSummary(label = "Last 30 days", entries = entries)
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
                        errorMessage = exception.message ?: "Failed to load reports"
                    )
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun toSummary(label: String, entries: List<WorkEntry>): ReportSummary {
        val completed = entries.count { it.status == Status.COMPLETED }
        val delivered = entries.count { it.status == Status.DELIVERED }
        val totalQuantity = entries.sumOf { it.quantity ?: 0 }
        return ReportSummary(
            label = label,
            completed = completed,
            delivered = delivered,
            totalQuantity = totalQuantity
        )
    }
}


