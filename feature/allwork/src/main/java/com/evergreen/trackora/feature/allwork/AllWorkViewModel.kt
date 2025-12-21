package com.evergreen.trackora.feature.allwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.usecase.GetAllWorkEntriesUseCase
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
    private val getAllWorkEntriesUseCase: GetAllWorkEntriesUseCase
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

    fun setFilter(status: Status?) {
        _uiState.update { it.copy(filter = status) }
    }
}


