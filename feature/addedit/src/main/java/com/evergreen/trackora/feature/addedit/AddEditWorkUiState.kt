package com.evergreen.trackora.feature.addedit

import com.evergreen.trackora.domain.model.Status
import java.time.LocalDate

/**
 * UI state for the Add/Edit Work screen.
 */
data class AddEditWorkUiState(
    val title: String = "",
    val quantityInput: String = "",
    val status: Status = Status.IN_PROGRESS,
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
) {
    val isValid: Boolean
        get() = title.isNotBlank()
}


