package com.evergreen.trackora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.theme.AppThemeMode
import com.evergreen.trackora.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val selectedLocale: AppLocale = AppLocale.SYSTEM,
    val selectedTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val customFields: CustomFields = CustomFields()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager,
    private val themeManager: ThemeManager,
    private val customFieldsManager: CustomFieldsManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        localeManager.localeFlow,
        themeManager.themeFlow,
        customFieldsManager.allCustomFields
    ) { locale, theme, customFields ->
        SettingsUiState(
            selectedLocale = locale,
            selectedTheme = theme,
            customFields = customFields
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onLocaleSelected(appLocale: AppLocale) {
        viewModelScope.launch {
            localeManager.setLocale(appLocale)
        }
    }

    fun onThemeSelected(mode: AppThemeMode) {
        viewModelScope.launch {
            themeManager.setTheme(mode)
        }
    }

    fun onCustomFieldsChanged(customFields: CustomFields) {
        viewModelScope.launch {
            customFieldsManager.setCustomFields(customFields)
        }
    }
}



