package com.evergreen.trackora.feature.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntryByIdUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    customFieldsManager: CustomFieldsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val currentEntryId: Long? = savedStateHandle["entryId"]

    private val _uiState = MutableStateFlow(AddEditWorkUiState())
    val uiState: StateFlow<AddEditWorkUiState> = _uiState.asStateFlow()

    // Custom field names from settings
    val customFieldNames = customFieldsManager.allCustomFields.map { fields: CustomFields ->
        Triple(fields.field1Name, fields.field2Name, fields.field3Name)
    }

    init {
        currentEntryId?.let { id ->
            loadEntry(id)
        }
    }

    fun onTitleChange(value: String) {
        val trimmed = value.trimStart()
        val titleError = when {
            trimmed.isBlank() && value.isNotEmpty() -> null // Don't show error while typing
            trimmed.length > 100 -> "Title must be 100 characters or less"
            else -> null
        }
        _uiState.update {
            it.copy(
                title = trimmed,
                titleError = titleError,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onDescriptionChange(value: String) {
        val trimmed = if (value.length <= 500) value.trimStart() else value.take(500).trimStart()
        _uiState.update {
            it.copy(
                description = trimmed,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onCustomField1Change(value: String) {
        _uiState.update {
            it.copy(
                customField1 = value,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onCustomField2Change(value: String) {
        _uiState.update {
            it.copy(
                customField2 = value,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onCustomField3Change(value: String) {
        _uiState.update {
            it.copy(
                customField3 = value,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onQuantityChange(value: String) {
        val sanitized = value.filter { it.isDigit() }
        val quantityError = when {
            sanitized.isEmpty() -> null
            sanitized.toIntOrNull()?.let { it <= 0 } == true -> "Quantity must be greater than 0"
            sanitized.toIntOrNull()?.let { it > 999999 } == true -> "Quantity is too large"
            else -> null
        }
        _uiState.update {
            it.copy(
                quantityInput = sanitized,
                quantityError = quantityError,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onStatusChange(status: Status) {
        _uiState.update { it.copy(status = status, isSaved = false) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date, showDatePicker = false, isSaved = false) }
    }

    fun showDatePicker() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    fun dismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
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
                            description = entry.description ?: "",
                            quantityInput = entry.quantity?.toString() ?: "",
                            customField1 = entry.customField1 ?: "",
                            customField2 = entry.customField2 ?: "",
                            customField3 = entry.customField3 ?: "",
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

        // Validate title
        val titleError = when {
            currentState.title.isBlank() -> "Title is required"
            currentState.title.length > 100 -> "Title must be 100 characters or less"
            else -> null
        }

        // Validate quantity if provided
        val quantityError = when {
            currentState.quantityInput.isEmpty() -> null
            currentState.quantityInput.toIntOrNull() == null -> "Please enter a valid number"
            currentState.quantityInput.toIntOrNull()
                ?.let { it <= 0 } == true -> "Quantity must be greater than 0"

            currentState.quantityInput.toIntOrNull()
                ?.let { it > 999999 } == true -> "Quantity is too large"

            else -> null
        }

        if (titleError != null || quantityError != null) {
            _uiState.update {
                it.copy(
                    titleError = titleError,
                    quantityError = quantityError,
                    errorMessage = titleError ?: quantityError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    titleError = null,
                    quantityError = null
                )
            }
            try {
                val quantity = currentState.quantityInput.toIntOrNull()
                val description = currentState.description.trim().takeIf { it.isNotBlank() }
                val customField1 = currentState.customField1.trim().takeIf { it.isNotBlank() }
                val customField2 = currentState.customField2.trim().takeIf { it.isNotBlank() }
                val customField3 = currentState.customField3.trim().takeIf { it.isNotBlank() }
                val entry = WorkEntry(
                    id = currentEntryId ?: 0,
                    title = currentState.title.trim(),
                    description = description,
                    quantity = quantity,
                    status = currentState.status,
                    date = currentState.date,
                    customField1 = customField1,
                    customField2 = customField2,
                    customField3 = customField3
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


