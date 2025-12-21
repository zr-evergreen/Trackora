package com.evergreen.trackora.feature.addedit

import com.evergreen.trackora.domain.model.Status
import java.time.LocalDate

/**
 * UI state for the Add/Edit Work screen.
 */
/**
 * UI state for the Add/Edit Work screen.
 * Follows immutability principles with computed properties.
 */
data class AddEditWorkUiState(
    val title: String = "",
    val description: String = "",
    val quantityInput: String = "",
    val customField1: String = "",
    val customField2: String = "",
    val customField3: String = "",
    val status: Status = Status.IN_PROGRESS,
    val date: LocalDate = LocalDate.now(),
    val showDatePicker: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val titleError: String? = null,
    val quantityError: String? = null
) {
    /**
     * Checks if the form is valid for submission.
     * Title must be non-blank and there should be no validation errors.
     */
    val isValid: Boolean
        get() = title.isNotBlank() && titleError == null && quantityError == null

    /**
     * Checks if the user has made any changes to the form.
     * Used to determine if save button should be enabled.
     */
    val hasChanges: Boolean
        get() = title.isNotBlank() ||
                description.isNotBlank() ||
                quantityInput.isNotBlank() ||
                customField1.isNotBlank() ||
                customField2.isNotBlank() ||
                customField3.isNotBlank()

    /**
     * Checks if the form is in a loading or saving state.
     */
    val isProcessing: Boolean
        get() = isLoading || isSaving
}


