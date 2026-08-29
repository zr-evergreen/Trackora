package com.evergreen.trackora.feature.allwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.DeleteWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.GetAllWorkEntriesUseCase
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
import javax.inject.Inject

/**
 * ViewModel for the All Work screen.
 */
@HiltViewModel
class AllWorkViewModel @Inject constructor(
    private val getAllWorkEntriesUseCase: GetAllWorkEntriesUseCase,
    private val deleteWorkEntryUseCase: DeleteWorkEntryUseCase,
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase,
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllWorkUiState(isLoading = true))
    val uiState: StateFlow<AllWorkUiState> = _uiState.asStateFlow()

    init {
        observeEntries()
    }

    private fun observeEntries() {
        viewModelScope.launch {
            getAllWorkEntriesUseCase()
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
                            entries = entries,
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
        observeEntries()
    }

    fun setFilter(status: Status?) {
        _uiState.update { it.copy(filter = status) }
    }

    /**
     * Deletes [entry] and keeps it in state so it can be put back.
     *
     * Undo re-inserts rather than soft-deleting. A deleted-flag column would
     * mean every query in the app grows a filter and a stale row lingers in
     * the database forever; re-insert keeps the schema honest and the window
     * for it is only as long as the snackbar.
     *
     * The re-inserted row gets a new id, which is invisible to the user — ids
     * are not shown anywhere and nothing references an entry by id across a
     * session.
     */
    /**
     * Moves [entry] one step along the lifecycle, matching Today.
     *
     * All Work previously offered no way to change a status at all — the user
     * had to open the entry and use the form. Since this is the screen where
     * older work is found, it is exactly where a forgotten job gets delivered.
     */
    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
    }

    fun advanceStatus(entry: WorkEntry) {
        val next = when (entry.status) {
            Status.IN_PROGRESS -> Status.COMPLETED
            Status.COMPLETED -> Status.DELIVERED
            Status.DELIVERED -> return
        }
        viewModelScope.launch {
            try {
                updateWorkEntryUseCase(entry.copy(status = next))
                _uiState.update { it.copy(recentlyAdvanced = entry) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    /** Writes the held pre-change entry back, rather than guessing a reverse step. */
    fun undoAdvance() {
        val previous = _uiState.value.recentlyAdvanced ?: return
        viewModelScope.launch {
            try {
                updateWorkEntryUseCase(previous)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(recentlyAdvanced = null) }
            }
        }
    }

    fun clearRecentlyAdvanced() {
        _uiState.update { it.copy(recentlyAdvanced = null) }
    }

    fun deleteEntry(entry: WorkEntry) {
        viewModelScope.launch {
            try {
                deleteWorkEntryUseCase(entry)
                _uiState.update { it.copy(recentlyDeleted = entry) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_DELETE_ENTRY)
                }
            }
        }
    }

    fun undoDelete() {
        val entry = _uiState.value.recentlyDeleted ?: return
        viewModelScope.launch {
            try {
                insertWorkEntryUseCase(entry.copy(id = 0))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_RESTORE_ENTRY)
                }
            } finally {
                _uiState.update { it.copy(recentlyDeleted = null) }
            }
        }
    }

    /** Called once the snackbar has gone, so a stale entry cannot be restored later. */
    fun clearRecentlyDeleted() {
        _uiState.update { it.copy(recentlyDeleted = null) }
    }
}


