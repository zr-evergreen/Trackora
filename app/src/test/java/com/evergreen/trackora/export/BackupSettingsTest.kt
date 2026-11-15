package com.evergreen.trackora.export

import com.evergreen.trackora.backup.BackupApp
import com.evergreen.trackora.backup.BackupCheck
import com.evergreen.trackora.backup.BackupLanguage
import com.evergreen.trackora.backup.BackupReader
import com.evergreen.trackora.backup.BackupSettings
import com.evergreen.trackora.backup.BackupTheme
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.theme.AppThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/** Every theme, language, reminder and custom-field setting goes into a backup and comes back as it was. */
class BackupSettingsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun everyThemeAndLanguageMapsToItsBackupValue() {
        assertEquals(
            listOf(BackupTheme.SYSTEM, BackupTheme.LIGHT, BackupTheme.DARK),
            AppThemeMode.entries.map { backupSettingsOf(CustomFields(), true, it, AppLocale.SYSTEM).theme },
        )
        assertEquals(
            listOf(BackupLanguage.SYSTEM, BackupLanguage.EN, BackupLanguage.FA),
            AppLocale.entries.map { backupSettingsOf(CustomFields(), true, AppThemeMode.SYSTEM, it).language },
        )
    }

    @Test
    fun unsetSettingsAreTheirDefaults() {
        // What the settings managers give for an untouched app: no names, reminder on, SYSTEM theme and language.
        assertEquals(
            BackupSettings("", "", "", true, BackupTheme.SYSTEM, BackupLanguage.SYSTEM),
            backupSettingsOf(CustomFields(), true, AppThemeMode.SYSTEM, AppLocale.SYSTEM),
        )
    }

    @Test
    fun everyCombinationComesBackFromABackup() {
        val names = listOf(
            CustomFields(),
            CustomFields("Client", "", ""),
            CustomFields("", "رنگ", "Size / اندازه"),
            CustomFields("a".repeat(500), "😀", "\"quoted\" \\ /"),
        )
        var round = 0
        for (theme in AppThemeMode.entries) for (locale in AppLocale.entries) for (reminder in listOf(true, false)) {
            val settings = backupSettingsOf(names[round % names.size], reminder, theme, locale)
            val export = BackupExport(
                workFolder = File(temp.root, "work-${round++}"),
                photoFolder = { null },
                isRefused = { false },
                readSettings = { settings },
                readEntries = { null },
                app = BackupApp("com.evergreen.trackora", "1", 1),
                databaseVersion = 2,
                now = { Instant.parse("2026-11-15T09:30:00Z") },
                zone = { ZoneOffset.UTC },
            )
            val ready = runBlocking { export.build() } as BackupBuild.Ready
            val result = BackupReader.verify(ready.file) as BackupCheck.Valid
            assertEquals("$theme $locale $reminder", settings, result.backup.settings)
        }
    }
}
