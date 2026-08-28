package com.evergreen.trackora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.usecase.GetAllWorkEntriesUseCase
import com.evergreen.trackora.export.WorkEntryCsv
import com.evergreen.trackora.export.WorkEntryExporter
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.theme.AppThemeMode
import com.evergreen.trackora.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val selectedLocale: AppLocale = AppLocale.SYSTEM,
    val selectedTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val customFields: CustomFields = CustomFields()
)

/**
 * One-shot outcomes of tapping Export. Modelled as events rather than state so
 * a share sheet is not re-launched when the screen recomposes or the device
 * rotates.
 */
sealed interface SettingsEvent {
    data class ShareCsv(val uri: Uri) : SettingsEvent
    data object NothingToExport : SettingsEvent
    data object ExportFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager,
    private val themeManager: ThemeManager,
    private val customFieldsManager: CustomFieldsManager,
    private val getAllWorkEntries: GetAllWorkEntriesUseCase,
    private val exporter: WorkEntryExporter
) : ViewModel() {

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

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

    /** Chooser intent for a written export, so the screen never touches the exporter. */
    fun shareIntent(uri: Uri, chooserTitle: String) = exporter.shareIntent(uri, chooserTitle)

    /**
     * Builds a CSV of every entry and emits a share event.
     *
     * [headers] arrives from the screen because the column titles are string
     * resources; the view model has no Context and should not be resolving
     * them itself.
     *
     * An empty database is reported rather than exported: handing someone a
     * file containing only a header row looks like the feature failed.
     */
    fun exportCsv(headers: WorkEntryCsv.Headers) {
        viewModelScope.launch {
            val event = try {
                val entries = getAllWorkEntries().first()
                if (entries.isEmpty()) {
                    SettingsEvent.NothingToExport
                } else {
                    val csv = WorkEntryCsv.format(
                        entries = entries,
                        customFields = customFieldsManager.allCustomFields.first(),
                        headers = headers
                    )
                    SettingsEvent.ShareCsv(exporter.writeCsv(csv))
                }
            } catch (e: Exception) {
                SettingsEvent.ExportFailed
            }
            events.send(event)
        }
    }
}



