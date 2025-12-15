package com.evergreen.trackora.feature.allwork

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry

/**
 * UI state for the All Work screen.
 */
data class AllWorkUiState(
    val entries: List<WorkEntry> = emptyList(),
    val filter: Status? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val filteredEntries: List<WorkEntry>
        get() = filter?.let { status -> entries.filter { it.status == status } } ?: entries
}


