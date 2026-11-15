package com.evergreen.trackora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.usecase.GetAllWorkEntriesUseCase
import com.evergreen.trackora.export.BackupBuild
import com.evergreen.trackora.export.BackupExport
import com.evergreen.trackora.export.BackupFailure
import com.evergreen.trackora.export.BackupProgress
import com.evergreen.trackora.export.BackupSave
import com.evergreen.trackora.export.BackupSummary
import com.evergreen.trackora.export.WorkEntryCsv
import com.evergreen.trackora.export.WorkEntryExporter
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.settings.ReminderPreferences
import com.evergreen.trackora.theme.AppThemeMode
import com.evergreen.trackora.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val selectedLocale: AppLocale = AppLocale.SYSTEM,
    val selectedTheme: AppThemeMode = AppThemeMode.SYSTEM,
    val customFields: CustomFields = CustomFields(),
    val reminderEnabled: Boolean = ReminderPreferences.DEFAULT_ENABLED
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

    /** A verified backup is ready: ask where to save it, suggesting [fileName]. */
    data class ChooseBackupDestination(val fileName: String) : SettingsEvent
    data object BackupNotSaved : SettingsEvent
    data object BackupCancelled : SettingsEvent
}

/** Where a backup is in being made and saved, for the dialog that shows it. */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Building(val progress: BackupProgress?) : BackupUiState

    /** Built and verified; the system's save dialog is open. */
    data object ChoosingDestination : BackupUiState
    data object Saving : BackupUiState
    data class Saved(val fileName: String, val summary: BackupSummary) : BackupUiState
    data class Failed(val reason: BackupFailure) : BackupUiState
    data object Refused : BackupUiState
}

/** The place the user chose to save a backup, as the screen opens it. */
interface BackupDestination {
    fun open(): OutputStream?
    fun reopen(): InputStream?

    /** The saved file's name as the place shows it, which the user may have changed. */
    fun displayName(): String?
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager,
    private val themeManager: ThemeManager,
    private val customFieldsManager: CustomFieldsManager,
    private val reminderPreferences: ReminderPreferences,
    private val getAllWorkEntries: GetAllWorkEntriesUseCase,
    private val exporter: WorkEntryExporter,
    private val backupExport: BackupExport,
) : ViewModel() {

    private val backup = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = backup.asStateFlow()
    private var backupJob: Job? = null
    private var backupTicket: BackupExport.Ticket? = null

    /** The verified backup waiting for a destination; its private copy is deleted once saved or abandoned. */
    private var ready: BackupBuild.Ready? = null

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow: Flow<SettingsEvent> = events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        localeManager.localeFlow,
        themeManager.themeFlow,
        customFieldsManager.allCustomFields,
        reminderPreferences.enabled
    ) { locale, theme, customFields, reminderEnabled ->
        SettingsUiState(
            selectedLocale = locale,
            selectedTheme = theme,
            customFields = customFields,
            reminderEnabled = reminderEnabled
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

    fun onReminderEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { reminderPreferences.setEnabled(enabled) }
    }

    /**
     * Builds and verifies a backup, then asks the screen for a place to save
     * it. Nothing is saved anywhere until the backup has passed its check.
     */
    fun startBackup() {
        if (backup.value != BackupUiState.Idle) return
        backup.value = BackupUiState.Building(null)
        val ticket = backupExport.ticket().also { backupTicket = it }
        backupJob = viewModelScope.launch {
            when (val result = backupExport.build(ticket, ::showProgress)) {
                is BackupBuild.Ready -> {
                    ready = result
                    backup.value = BackupUiState.ChoosingDestination
                    events.send(SettingsEvent.ChooseBackupDestination(result.fileName))
                }
                BackupBuild.Refused -> backup.value = BackupUiState.Refused
                is BackupBuild.Failed -> backup.value = BackupUiState.Failed(result.reason)
            }
        }
    }

    /**
     * Progress arrives from the build's own thread, and may come just after
     * Cancel: it only ever updates a backup still being built.
     */
    private fun showProgress(progress: BackupProgress) {
        while (true) {
            val current = backup.value
            if (current !is BackupUiState.Building) return
            if (backup.compareAndSet(current, BackupUiState.Building(progress))) return
        }
    }

    /** Stops a backup being built, and deletes what it had written. */
    fun cancelBackup() {
        if (backup.value !is BackupUiState.Building) return
        stopBuilding()
        backup.value = BackupUiState.Idle
        viewModelScope.launch { events.send(SettingsEvent.BackupCancelled) }
    }

    /** The save dialog's answer: where to save, or null if it was dismissed. */
    fun onBackupDestination(destination: BackupDestination?) {
        val built = ready ?: return
        if (destination == null) {
            discardReady()
            backup.value = BackupUiState.Idle
            viewModelScope.launch { events.send(SettingsEvent.BackupNotSaved) }
            return
        }
        backup.value = BackupUiState.Saving
        backupJob = viewModelScope.launch {
            val result = backupExport.save(built, destination::open, destination::reopen)
            val name = if (result is BackupSave.Saved) {
                withContext(Dispatchers.IO) { runCatching { destination.displayName() }.getOrNull() } ?: built.fileName
            } else {
                null
            }
            discardReady()
            backup.value = when (result) {
                is BackupSave.Saved -> BackupUiState.Saved(name ?: built.fileName, result.summary)
                is BackupSave.Failed -> BackupUiState.Failed(result.reason)
            }
        }
    }

    /** Closes the result of a backup. */
    fun dismissBackup() {
        when (backup.value) {
            is BackupUiState.Saved, is BackupUiState.Failed, BackupUiState.Refused -> backup.value = BackupUiState.Idle
            else -> Unit
        }
    }

    private fun discardReady() {
        ready?.let(backupExport::discard)
        ready = null
    }

    /**
     * Cancels the build, and once it has stopped deletes its folder, in case
     * it finished at the same moment and its result was never seen.
     */
    private fun stopBuilding() {
        val job = backupJob ?: return
        val ticket = backupTicket
        job.invokeOnCompletion { ticket?.let(backupExport::discard) }
        job.cancel()
    }

    override fun onCleared() {
        if (backup.value is BackupUiState.Building) stopBuilding()
        discardReady()
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



