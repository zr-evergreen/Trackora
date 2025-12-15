package com.evergreen.trackora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.locale.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val selectedLocale: AppLocale = AppLocale.SYSTEM
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = localeManager.localeFlow
        .map { locale -> SettingsUiState(selectedLocale = locale) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun onLocaleSelected(appLocale: AppLocale) {
        viewModelScope.launch {
            localeManager.setLocale(appLocale)
        }
    }
}

