package com.evergreen.trackora.export

import android.content.Context
import com.evergreen.trackora.BuildConfig
import com.evergreen.trackora.backup.BackupApp
import com.evergreen.trackora.backup.BackupLanguage
import com.evergreen.trackora.backup.BackupSettings
import com.evergreen.trackora.backup.BackupTheme
import com.evergreen.trackora.data.local.database.DatabaseDamage
import com.evergreen.trackora.data.local.database.StoredEntries
import com.evergreen.trackora.data.local.database.TrackoraDatabase
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.settings.ReminderPreferences
import com.evergreen.trackora.theme.AppThemeMode
import com.evergreen.trackora.theme.ThemeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Singleton

/** Wires [BackupExport] to the app's database, settings and folders. */
@Module
@InstallIn(SingletonComponent::class)
object BackupExportModule {

    @Provides
    @Singleton
    fun provideBackupExport(
        @ApplicationContext context: Context,
        database: TrackoraDatabase,
        customFields: CustomFieldsManager,
        reminder: ReminderPreferences,
        theme: ThemeManager,
        locale: LocaleManager,
    ): BackupExport = BackupExport(
        // no_backup: Auto Backup never copies it, and nothing else uses this folder.
        workFolder = File(context.noBackupFilesDir, "backup-export"),
        // Where every app photo link points: camera photos, gallery copies, later restored ones.
        photoFolder = { context.getExternalFilesDir(null) },
        isRefused = { DatabaseDamage.isRefusedUntilRestart(context) },
        readSettings = {
            backupSettingsOf(customFields.getCustomFields(), reminder.enabled.first(), theme.themeFlow.first(), locale.localeFlow.first())
        },
        readEntries = { onRow -> StoredEntries.read(context, database, onRow) },
        app = BackupApp(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()),
        databaseVersion = TrackoraDatabase.VERSION,
    )
}

/**
 * The settings as the app sees them: unset custom fields are "", and an unset
 * reminder, theme or language is its default (on, SYSTEM, SYSTEM), exactly as
 * the settings screen shows them.
 */
internal fun backupSettingsOf(
    customFields: CustomFields,
    reminderEnabled: Boolean,
    theme: AppThemeMode,
    locale: AppLocale,
): BackupSettings = BackupSettings(
    customField1Name = customFields.field1Name,
    customField2Name = customFields.field2Name,
    customField3Name = customFields.field3Name,
    reminderEnabled = reminderEnabled,
    theme = when (theme) {
        AppThemeMode.SYSTEM -> BackupTheme.SYSTEM
        AppThemeMode.LIGHT -> BackupTheme.LIGHT
        AppThemeMode.DARK -> BackupTheme.DARK
    },
    language = when (locale) {
        AppLocale.SYSTEM -> BackupLanguage.SYSTEM
        AppLocale.EN -> BackupLanguage.EN
        AppLocale.FA -> BackupLanguage.FA
    },
)
