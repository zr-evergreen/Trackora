package com.evergreen.trackora

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.backup.BackupApp
import com.evergreen.trackora.backup.BackupCheck
import com.evergreen.trackora.backup.BackupEntry
import com.evergreen.trackora.backup.BackupLanguage
import com.evergreen.trackora.backup.BackupPhoto
import com.evergreen.trackora.backup.BackupReader
import com.evergreen.trackora.backup.BackupSettings
import com.evergreen.trackora.backup.BackupStatus
import com.evergreen.trackora.backup.BackupTheme
import com.evergreen.trackora.backup.MissingPhoto
import com.evergreen.trackora.backup.PhotoExtraction
import com.evergreen.trackora.data.local.database.StoredEntries
import com.evergreen.trackora.data.local.database.TrackoraDatabase
import com.evergreen.trackora.export.BackupBuild
import com.evergreen.trackora.export.BackupExport
import com.evergreen.trackora.export.BackupExportModule
import com.evergreen.trackora.export.BackupSave
import com.evergreen.trackora.export.backupSettingsOf
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.settings.ReminderPreferences
import com.evergreen.trackora.theme.ThemeManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Backup export on Android against a real database (opened the way the app
 * opens it, under its own test name), the real settings managers over their
 * own DataStore files, and real photo files. Synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class BackupExportDeviceTest {

    @get:Rule
    val testName = TestName()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var name: String
    private lateinit var database: TrackoraDatabase
    private lateinit var root: File
    private lateinit var photos: File
    private lateinit var settingsStore: DataStore<Preferences>
    private lateinit var themeStore: DataStore<Preferences>
    private lateinit var localeStore: DataStore<Preferences>

    @Before
    fun setUp() {
        name = "backup-export-${testName.methodName}"
        context.deleteDatabase(name)
        root = File(context.cacheDir, name).apply { deleteRecursively(); mkdirs() }
        photos = File(root, "files").apply { mkdirs() }
        // Each test its own DataStore files: DataStore allows one instance per file in a process.
        settingsStore = PreferenceDataStoreFactory.create { File(root, "settings_prefs.preferences_pb") }
        themeStore = PreferenceDataStoreFactory.create { File(root, "theme_prefs.preferences_pb") }
        localeStore = PreferenceDataStoreFactory.create { File(root, "locale_prefs.preferences_pb") }
        database = TrackoraDatabase.build(context, name)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
        File(context.getDatabasePath(name).path + ".damaged").delete()
        context.deleteSharedPreferences("$name-launch")
        root.deleteRecursively()
    }

    private fun sql(statement: String, vararg args: Any?) = database.openHelper.writableDatabase.execSQL(statement, args)

    private fun insert(id: Long?, title: String, quantity: Long?, status: String, date: String, photo: String?, description: String? = null) =
        sql(
            "INSERT INTO work_entries (id, title, description, quantity, status, date, customField1, customField2, customField3, photoUri) VALUES (?, ?, ?, ?, ?, ?, 'c1', NULL, '', ?)",
            id, title, description, quantity, status, date, photo,
        )

    private fun link(path: String) = "content://${context.packageName}.fileprovider/external_files/$path"

    private val customFields by lazy { CustomFieldsManager(settingsStore) }
    private val reminder by lazy { ReminderPreferences(settingsStore) }
    private val theme by lazy { ThemeManager(themeStore, context.getSharedPreferences("$name-launch", Context.MODE_PRIVATE)) }
    private val locale by lazy { LocaleManager(localeStore) }

    private fun export(refused: () -> Boolean = { false }) = BackupExport(
        workFolder = File(root, "backup-export"),
        photoFolder = { photos },
        isRefused = refused,
        readSettings = {
            backupSettingsOf(customFields.getCustomFields(), reminder.enabled.first(), theme.themeFlow.first(), locale.localeFlow.first())
        },
        readEntries = { onRow -> StoredEntries.read(context, database, onRow) },
        app = BackupApp(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong()),
        databaseVersion = TrackoraDatabase.VERSION,
    )

    private fun seed() {
        File(photos, "IMG_20261115_090000.jpg").writeBytes(ByteArray(70_000) { (it * 31).toByte() })
        File(photos, "restored/r1").mkdirs()
        File(photos, "restored/r1/IMG_20261115_090000.jpg").writeBytes("same name, other folder".toByteArray())
        insert(Long.MIN_VALUE, "", Long.MIN_VALUE, "IN_PROGRESS", "-999999999-01-01", null)
        insert(0, "تعمیر لپ‌تاپ 👩🏽‍🔧", Int.MAX_VALUE + 1L, "COMPLETED", "2026-11-15", link("IMG_20261115_090000.jpg"), "d".repeat(100_000))
        insert(5, "shared photo", null, "DELIVERED", "2026-11-14", link("IMG_20261115_090000.jpg"))
        insert(6, "restored photo", 1, "DELIVERED", "2026-11-13", link("restored/r1/IMG_20261115_090000.jpg"))
        insert(7, "missing photo", 2, "COMPLETED", "2026-11-12", link("IMG_gone.jpg"))
        insert(8, "picker link", 3, "COMPLETED", "2026-11-11", "content://com.android.providers.media.documents/document/image%3A12")
        insert(Long.MAX_VALUE - 1, "deleted after this", 4, "COMPLETED", "+999999999-12-31", null)
        insert(Long.MAX_VALUE, "to delete", 5, "COMPLETED", "2026-11-10", null)
        sql("DELETE FROM work_entries WHERE id = ?", Long.MAX_VALUE)
        runBlocking {
            settingsStore.edit {
                it[stringPreferencesKey("custom_field_1_name")] = "رنگ"
                it[stringPreferencesKey("custom_field_3_name")] = "Size"
                it[booleanPreferencesKey("daily_reminder_enabled")] = false
            }
            themeStore.edit { it[stringPreferencesKey("app_theme")] = "DARK" }
            localeStore.edit { it[stringPreferencesKey("app_locale")] = "fa" }
        }
    }

    @Test
    fun aBackupOfARealDatabaseIsSavedAndReadsBackExactly() {
        seed()
        val export = export()
        val ready = runBlocking { export.build() }
        assertTrue("$ready", ready is BackupBuild.Ready)
        ready as BackupBuild.Ready

        val saved = File(root, "saved/${ready.fileName}").apply { parentFile!!.mkdirs() }
        val result = runBlocking { export.save(ready, { saved.outputStream() }, { saved.inputStream() }) }
        assertEquals(BackupSave.Saved(ready.summary), result)
        export.discard(ready)

        val entries = ArrayList<BackupEntry>()
        val check = BackupReader.readEntries(saved) { entries += it }
        assertTrue("$check", check is BackupCheck.Valid)
        val backup = (check as BackupCheck.Valid).backup

        assertEquals(BackupSettings("رنگ", "", "Size", false, BackupTheme.DARK, BackupLanguage.FA), backup.settings)
        assertEquals(Long.MAX_VALUE, backup.manifest.idHighWaterMark)
        assertEquals(listOf(Long.MIN_VALUE, 0L, 5L, 6L, 7L, 8L, Long.MAX_VALUE - 1), entries.map { it.id })
        assertEquals(listOf(Long.MIN_VALUE, Int.MAX_VALUE + 1L, null, 1L, 2L, 3L, 4L), entries.map { it.quantity })
        assertEquals(listOf("", "تعمیر لپ‌تاپ 👩🏽‍🔧"), entries.take(2).map { it.title })
        assertEquals("d".repeat(100_000), entries[1].description)
        assertEquals(listOf("-999999999-01-01", "+999999999-12-31"), listOf(entries.first().date, entries.last().date))
        assertEquals(BackupStatus.IN_PROGRESS, entries.first().status)
        assertEquals(listOf("c1"), entries.map { it.customField1 }.distinct())
        assertEquals(listOf(""), entries.map { it.customField3 }.distinct())
        assertEquals(
            listOf(
                null,
                BackupPhoto.included("IMG_20261115_090000.jpg", link("IMG_20261115_090000.jpg")),
                BackupPhoto.included("IMG_20261115_090000.jpg", link("IMG_20261115_090000.jpg")),
                BackupPhoto.included("IMG_20261115_090000-2.jpg", link("restored/r1/IMG_20261115_090000.jpg")),
                BackupPhoto.missing(link("IMG_gone.jpg"), MissingPhoto.NOT_FOUND),
                BackupPhoto.missing("content://com.android.providers.media.documents/document/image%3A12", MissingPhoto.NOT_APP_FILE),
                null,
            ),
            entries.map { it.photo },
        )
        assertEquals(2, backup.manifest.photoCount)
        val out = File(root, "extracted").apply { mkdirs() }
        val extracted = BackupReader.extractPhoto(saved, "IMG_20261115_090000.jpg", out) as PhotoExtraction.Extracted
        assertArrayEquals(File(photos, "IMG_20261115_090000.jpg").readBytes(), extracted.file.readBytes())
    }

    @Test
    fun exportingChangesNothingItReads() {
        seed()
        val db = database.openHelper.writableDatabase
        val before = listOf(fingerprint(name, ""), fingerprint(name, "-wal")) + tree(photos) + tree(root, ".preferences_pb")
        val dataVersion = db.query("PRAGMA data_version").use { it.moveToFirst(); it.getLong(0) }
        val changes = db.query("SELECT total_changes()").use { it.moveToFirst(); it.getLong(0) }
        val rows = db.query("SELECT * FROM work_entries ORDER BY id").use { c -> generateSequence { if (c.moveToNext()) (0 until c.columnCount).joinToString("|") { i -> c.getString(i) ?: "∅" } else null }.toList() }

        val export = export()
        val ready = runBlocking { export.build() } as BackupBuild.Ready
        export.discard(ready)

        val after = listOf(fingerprint(name, ""), fingerprint(name, "-wal")) + tree(photos) + tree(root, ".preferences_pb")
        assertEquals("database, -wal, photos and settings files are byte for byte as they were", before, after)
        assertEquals(dataVersion, db.query("PRAGMA data_version").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(changes, db.query("SELECT total_changes()").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(rows, db.query("SELECT * FROM work_entries ORDER BY id").use { c -> generateSequence { if (c.moveToNext()) (0 until c.columnCount).joinToString("|") { i -> c.getString(i) ?: "∅" } else null }.toList() })
        assertFalse("nothing is left in the work folder", File(root, "backup-export").listFiles().orEmpty().any())
    }

    @Test
    fun aRefusedDatabaseIsNotExportedOrOpened() {
        seed()
        database.close()
        File(context.getDatabasePath(name).path + ".damaged").writeText("marker for a test")
        database = TrackoraDatabase.build(context, name)
        val result = runBlocking { export().build() }
        assertEquals(BackupBuild.Refused, result)
        assertFalse(database.isOpen)
        assertFalse(File(root, "backup-export").listFiles().orEmpty().any())
    }

    /** The app's own wiring: its folders, its app details, the real settings managers. */
    @Test
    fun theAppsWiringMakesABackup() {
        seed()
        val export = BackupExportModule.provideBackupExport(context, database, customFields, reminder, theme, locale)
        val ready = runBlocking { export.build() }
        assertTrue("$ready", ready is BackupBuild.Ready)
        ready as BackupBuild.Ready
        assertTrue(ready.fileName.matches(Regex("Trackora-backup-[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{4}\\.zip")))
        val check = BackupReader.verify(ready.file) as BackupCheck.Valid
        assertEquals(BuildConfig.APPLICATION_ID, check.backup.manifest.app.id)
        assertEquals(TrackoraDatabase.VERSION, check.backup.manifest.databaseVersion)
        assertTrue(ready.file.path.startsWith(File(context.noBackupFilesDir, "backup-export").path))
        export.discard(ready)
        assertFalse(File(context.noBackupFilesDir, "backup-export").listFiles().orEmpty().any())
    }

    private fun fingerprint(database: String, suffix: String): String {
        val f = File(context.getDatabasePath(database).path + suffix)
        return if (!f.exists()) "$suffix absent" else "$suffix ${sha256(f)}"
    }

    private fun tree(folder: File, suffix: String = ""): List<String> =
        folder.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) && !it.path.contains("backup-export") && !it.path.contains("saved") }
            .map { "${it.relativeTo(folder)} ${it.length()} ${it.lastModified()} ${sha256(it)}" }.sorted().toList()

    private fun sha256(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
}
