package com.evergreen.trackora.ui.workentry

import com.evergreen.trackora.domain.model.WorkEntry

/**
 * UI state for work entries screen.
 */
data class WorkEntryUiState(
    val entries: List<WorkEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = entries.isEmpty() && !isLoading
}

