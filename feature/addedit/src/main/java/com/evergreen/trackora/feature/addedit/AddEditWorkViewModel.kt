package com.evergreen.trackora.feature.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntryByIdUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for creating and editing work entries.
 */
@HiltViewModel
class AddEditWorkViewModel @Inject constructor(
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase,
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase,
    private val getWorkEntryByIdUseCase: GetWorkEntryByIdUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val currentEntryId: Long? = savedStateHandle["entryId"]

    private val _uiState = MutableStateFlow(AddEditWorkUiState())
    val uiState: StateFlow<AddEditWorkUiState> = _uiState.asStateFlow()

    init {
        currentEntryId?.let { id ->
            loadEntry(id)
        }
    }

    fun onTitleChange(value: String) {
        _uiState.update {
            it.copy(title = value, errorMessage = null, isSaved = false)
        }
    }

    fun onQuantityChange(value: String) {
        val sanitized = value.filter { it.isDigit() }
        _uiState.update { it.copy(quantityInput = sanitized, isSaved = false) }
    }

    fun onStatusChange(status: Status) {
        _uiState.update { it.copy(status = status, isSaved = false) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date, isSaved = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadEntry(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val entry = getWorkEntryByIdUseCase(id)
                if (entry != null) {
                    _uiState.update {
                        it.copy(
                            title = entry.title,
                            quantityInput = entry.quantity?.toString() ?: "",
                            status = entry.status,
                            date = entry.date,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Entry not found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load entry"
                    )
                }
            }
        }
    }

    fun save() {
        val currentState = _uiState.value
        if (currentState.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Title is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val quantity = currentState.quantityInput.toIntOrNull()
                val entry = WorkEntry(
                    id = currentEntryId ?: 0,
                    title = currentState.title.trim(),
                    description = null,
                    quantity = quantity,
                    status = currentState.status,
                    date = currentState.date
                )

                if (currentEntryId == null) {
                    insertWorkEntryUseCase(entry)
                } else {
                    updateWorkEntryUseCase(entry)
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "Unable to save entry"
                    )
                }
            }
        }
    }
}


