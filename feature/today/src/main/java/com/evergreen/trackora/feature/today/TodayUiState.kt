package com.evergreen.trackora.feature.today

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry

/**
 * UI state for the Today screen.
 */
data class TodayUiState(
    val todayEntries: List<WorkEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isEmpty: Boolean
        get() = todayEntries.isEmpty() && !isLoading
    
    val entryCount: Int
        get() = todayEntries.size
    
    val completedCount: Int
        get() = todayEntries.count { it.status == Status.COMPLETED }
    
    val deliveredCount: Int
        get() = todayEntries.count { it.status == Status.DELIVERED }
}

