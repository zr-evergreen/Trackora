package com.evergreen.trackora.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for the Today screen.
 * Observes today's work entries and handles adding/updating entries.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getWorkEntriesByDateUseCase: GetWorkEntriesByDateUseCase,
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase,
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()
    
    private val today: LocalDate = LocalDate.now()
    
    init {
        observeTodayEntries()
    }
    
    /**
     * Observe today's work entries.
     */
    private fun observeTodayEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            getWorkEntriesByDateUseCase(today)
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Failed to load entries"
                        )
                    }
                }
                .collect { entries ->
                    _uiState.update {
                        it.copy(
                            todayEntries = entries,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }
    
    /**
     * Add a new work entry for today.
     */
    fun addWorkEntry(
        title: String,
        description: String? = null,
        quantity: Int? = null,
        status: Status = Status.IN_PROGRESS
    ) {
        if (title.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Title cannot be empty")
            }
            return
        }
        
        viewModelScope.launch {
            try {
                val newEntry = WorkEntry(
                    title = title.trim(),
                    description = description?.trim(),
                    quantity = quantity,
                    status = status,
                    date = today
                )
                insertWorkEntryUseCase(newEntry)
                // State will be updated automatically via Flow
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to add entry")
                }
            }
        }
    }
    
    /**
     * Update the status of a work entry.
     */
    fun updateEntryStatus(entryId: Long, newStatus: Status) {
        viewModelScope.launch {
            try {
                val currentEntry = _uiState.value.todayEntries.find { it.id == entryId }
                if (currentEntry != null) {
                    val updatedEntry = currentEntry.copy(status = newStatus)
                    updateWorkEntryUseCase(updatedEntry)
                    // State will be updated automatically via Flow
                } else {
                    _uiState.update {
                        it.copy(errorMessage = "Entry not found")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to update status")
                }
            }
        }
    }
    
    /**
     * Update a work entry.
     */
    fun updateWorkEntry(entry: WorkEntry) {
        viewModelScope.launch {
            try {
                updateWorkEntryUseCase(entry)
                // State will be updated automatically via Flow
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to update entry")
                }
            }
        }
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

