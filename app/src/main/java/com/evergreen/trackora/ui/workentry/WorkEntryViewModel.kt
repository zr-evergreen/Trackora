package com.evergreen.trackora.ui.workentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.DeleteWorkEntryByIdUseCase
import com.evergreen.trackora.domain.usecase.GetAllWorkEntriesUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing work entries.
 * Follows MVVM pattern with StateFlow for UI state management.
 */
@HiltViewModel
class WorkEntryViewModel @Inject constructor(
    private val getAllWorkEntriesUseCase: GetAllWorkEntriesUseCase,
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase,
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase,
    private val deleteWorkEntryByIdUseCase: DeleteWorkEntryByIdUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(WorkEntryUiState())
    val uiState: StateFlow<WorkEntryUiState> = _uiState.asStateFlow()
    
    init {
        loadWorkEntries()
    }
    
    /**
     * Load all work entries.
     */
    fun loadWorkEntries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            getAllWorkEntriesUseCase()
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Unknown error occurred"
                        )
                    }
                }
                .collect { entries ->
                    _uiState.update {
                        it.copy(
                            entries = entries,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }
    
    /**
     * Insert a new work entry.
     */
    fun insertWorkEntry(entry: WorkEntry) {
        viewModelScope.launch {
            try {
                insertWorkEntryUseCase(entry)
                // State will be updated automatically via Flow
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to insert entry")
                }
            }
        }
    }
    
    /**
     * Update an existing work entry.
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
     * Delete a work entry by ID.
     */
    fun deleteWorkEntry(id: Long) {
        viewModelScope.launch {
            try {
                deleteWorkEntryByIdUseCase(id)
                // State will be updated automatically via Flow
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Failed to delete entry")
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

