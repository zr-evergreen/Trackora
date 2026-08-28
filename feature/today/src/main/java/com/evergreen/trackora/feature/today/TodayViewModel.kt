package com.evergreen.trackora.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetUndeliveredWorkUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import com.evergreen.trackora.util.AppConstants
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
    private val getUndeliveredWorkUseCase: GetUndeliveredWorkUseCase,
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase,
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private val today: LocalDate = LocalDate.now()

    init {
        observeTodayEntries()
        observeUndelivered()
    }

    /**
     * Work finished but not handed over, from any date.
     *
     * A separate subscription rather than a filter over today's entries: the
     * job that matters most here is usually the oldest one, and it is by
     * definition not from today.
     */
    private fun observeUndelivered() {
        viewModelScope.launch {
            getUndeliveredWorkUseCase()
                .catch { exception ->
                    // Surfaced rather than swallowed. An empty undelivered band
                    // and a failed undelivered query look identical on screen,
                    // and the band is the one thing this screen exists to show.
                    _uiState.update {
                        it.copy(
                            errorMessage = exception.message
                                ?: AppConstants.Errors.FAILED_TO_LOAD_ENTRIES
                        )
                    }
                }
                .collect { entries ->
                    _uiState.update { it.copy(undelivered = entries) }
                }
        }
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
                            errorMessage = exception.message
                                ?: AppConstants.Errors.FAILED_TO_LOAD_ENTRIES
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

    /** Re-subscribes after a load failure. The flow is cold, so collecting again retries. */
    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeTodayEntries()
    }

    fun addWorkEntry(
        title: String,
        description: String? = null,
        quantity: Int? = null,
        status: Status = Status.IN_PROGRESS
    ) {
        if (title.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = AppConstants.Errors.TITLE_CANNOT_BE_EMPTY)
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
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_ADD)
                }
            }
        }
    }

    /**
     * Records the minimum needed to capture a job: a title.
     *
     * Everything else takes a sensible default — in progress, dated today, no
     * quantity — because the point of quick add is to get the job written down
     * before the user forgets it. Quantity and the rest are a tap away in the
     * detail screen, and adding them here would defeat the feature.
     */
    fun quickAdd(title: String) {
        if (title.isBlank()) return
        addWorkEntry(title = title)
    }

    /**
     * Moves a job one step along the lifecycle.
     *
     * IN_PROGRESS becomes COMPLETED; COMPLETED becomes DELIVERED; DELIVERED is
     * terminal and does nothing. There is exactly one forward move from any
     * state, which is what lets the row carry a single button instead of the
     * three status chips it used to show beside a pill that already said the
     * same thing.
     *
     * One tap, no confirmation dialog. Both transitions are routine and
     * frequent, and a dialog on each would cost more than the occasional
     * mistake; the undo in the snackbar covers that instead.
     */
    fun advanceStatus(entry: WorkEntry) {
        val next = when (entry.status) {
            Status.IN_PROGRESS -> Status.COMPLETED
            Status.COMPLETED -> Status.DELIVERED
            Status.DELIVERED -> return
        }
        viewModelScope.launch {
            try {
                updateWorkEntryUseCase(entry.copy(status = next))
                // The pre-change entry is held, so undo restores the exact
                // previous status rather than guessing a reverse transition.
                _uiState.update { it.copy(recentlyAdvanced = entry) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_UPDATE_STATUS)
                }
            }
        }
    }

    fun undoAdvance() {
        val entry = _uiState.value.recentlyAdvanced ?: return
        viewModelScope.launch {
            try {
                updateWorkEntryUseCase(entry)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_UPDATE_STATUS)
                }
            } finally {
                _uiState.update { it.copy(recentlyAdvanced = null) }
            }
        }
    }

    fun clearRecentlyAdvanced() {
        _uiState.update { it.copy(recentlyAdvanced = null) }
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
                        it.copy(errorMessage = AppConstants.Errors.ENTRY_NOT_FOUND)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_UPDATE_STATUS)
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
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_UPDATE)
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

