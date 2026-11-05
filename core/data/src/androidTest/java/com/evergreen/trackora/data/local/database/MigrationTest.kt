package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.data.local.entity.WorkEntry
import com.evergreen.trackora.domain.model.Status
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Upgrades real databases of every past version and checks nothing is lost.
 *
 * Each old version is built from its exported schema in `core/data/schemas`,
 * filled with entries that cover every column — Persian text with half-spaces,
 * quotes and line breaks, missing optional fields, the largest quantity, every
 * status — and then upgraded. The app's own database
 * ([TrackoraDatabase.build]) opens the result, so these check the
 * configuration users get.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val name = "migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackoraDatabase::class.java
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    /** One entry as stored, in the columns every version has had. */
    private data class Row(
        val id: Long,
        val title: String,
        val description: String?,
        val quantity: Int?,
        val status: String,
        val date: String,
        val customField1: String?,
        val customField2: String?,
        val customField3: String?,
    )

    // Gaps in the ids on purpose: they are what deleted entries leave behind.
    private val version1Rows = listOf(
        Row(1, "دوخت پیراهن آبی", null, 3, "IN_PROGRESS", "2026-11-03", "کریمی", null, null),
        Row(2, "Hem trousers, \"long\"", "Customer said:\nshorter by 2 cm", 2, "COMPLETED", "2026-10-30", "Ahmadi", "J-42", "rush"),
        Row(5, "کت‌شلوار", "پارچهٔ کتان", null, "DELIVERED", "2024-02-29", "كريمی", "سفارش ۱۲", "تحویل در دو مرحله"),
        Row(9, "سفارش عمده", null, 999_999, "COMPLETED", "2025-03-20", null, null, null),
    )

    private fun SupportSQLiteDatabase.insert(row: Row) = execSQL(
        "INSERT INTO work_entries (id, title, description, quantity, status, date, customField1, customField2, customField3) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf(row.id, row.title, row.description, row.quantity, row.status, row.date, row.customField1, row.customField2, row.customField3)
    )

    private fun SupportSQLiteDatabase.rows(): List<Pair<Row, String?>> =
        query("SELECT id, title, description, quantity, status, date, customField1, customField2, customField3, photoUri FROM work_entries ORDER BY id").use { c ->
            fun text(i: Int) = if (c.isNull(i)) null else c.getString(i)
            generateSequence { if (c.moveToNext()) c else null }.map {
                Row(
                    id = c.getLong(0),
                    title = c.getString(1),
                    description = text(2),
                    quantity = if (c.isNull(3)) null else c.getInt(3),
                    status = c.getString(4),
                    date = c.getString(5),
                    customField1 = text(6),
                    customField2 = text(7),
                    customField3 = text(8),
                ) to text(9)
            }.toList()
        }

    private fun Row.asEntity(photoUri: String? = null) = WorkEntry(
        id = id,
        title = title,
        description = description,
        quantity = quantity,
        status = Status.valueOf(status),
        date = LocalDate.parse(date),
        customField1 = customField1,
        customField2 = customField2,
        customField3 = customField3,
        photoUri = photoUri,
    )

    private fun createVersion1() {
        helper.createDatabase(name, 1).use { db -> version1Rows.forEach { db.insert(it) } }
    }

    @Test
    fun upgradingFromVersion1KeepsEveryEntryAndEveryField() {
        createVersion1()

        // Also checks the upgraded schema is exactly version 2's.
        val db = helper.runMigrationsAndValidate(name, 2, true, *DatabaseMigrations.ALL)

        assertEquals(version1Rows.map { it to null }, db.rows())
    }

    @Test
    fun afterUpgradingFromVersion1TheAppReadsTheSameEntries() = runBlocking {
        createVersion1()

        val database = TrackoraDatabase.build(context, name)
        try {
            val entries = database.workEntryDao().getAllEntries().first().sortedBy { it.id }
            assertEquals(version1Rows.map { it.asEntity() }, entries)
        } finally {
            database.close()
        }
    }

    @Test
    fun newEntriesAfterAnUpgradeNeverReuseTheNumberOfAnOldOne() = runBlocking {
        helper.createDatabase(name, 1).use { db ->
            version1Rows.forEach { db.insert(it) }
            // The newest entry was deleted before the upgrade; its number
            // stays used, so nothing can later be mistaken for it.
            db.insert(version1Rows.last().copy(id = 12))
            db.execSQL("DELETE FROM work_entries WHERE id = 12")
        }

        val database = TrackoraDatabase.build(context, name)
        try {
            val id = database.workEntryDao().insertEntry(version1Rows.first().copy(id = 0).asEntity())
            assertEquals(13L, id)
        } finally {
            database.close()
        }
    }

    @Test
    fun theCurrentVersionOpensUnchangedWithItsPhotos() = runBlocking {
        val photo = "content://com.evergreen.trackora.fileprovider/external_files/IMG_20261103_101500.jpg"
        helper.createDatabase(name, TrackoraDatabase.VERSION).use { db ->
            version1Rows.forEach { db.insert(it) }
            db.execSQL("UPDATE work_entries SET photoUri = ? WHERE id = 2", arrayOf(photo))
        }

        val database = TrackoraDatabase.build(context, name)
        try {
            val entries = database.workEntryDao().getAllEntries().first().sortedBy { it.id }
            val expected = version1Rows.map { it.asEntity(photoUri = if (it.id == 2L) photo else null) }
            assertEquals(expected, entries)
        } finally {
            database.close()
        }
    }

    /**
     * A database the app has no path for — here one from a newer build, as
     * after installing an older version over it — must fail to open and keep
     * its version and every entry. The destructive fallback this replaced
     * deleted it and opened an empty one instead.
     */
    @Test
    fun aDatabaseWithNoUpgradePathIsNeverDeleted() {
        helper.createDatabase(name, TrackoraDatabase.VERSION).use { db ->
            version1Rows.forEach { db.insert(it) }
            db.execSQL("PRAGMA user_version = ${TrackoraDatabase.VERSION + 1}")
        }

        val database = TrackoraDatabase.build(context, name)
        try {
            database.openHelper.writableDatabase
            fail("Opened a database it has no migration path for")
        } catch (expected: IllegalStateException) {
            // Refused, as it should.
        } finally {
            database.close()
        }

        val file = context.getDatabasePath(name)
        assertTrue("The database file was deleted", file.exists())
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(TrackoraDatabase.VERSION + 1, db.version)
            db.rawQuery("SELECT id FROM work_entries ORDER BY id", null).use { c ->
                val ids = generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList()
                assertEquals(version1Rows.map { it.id }, ids)
            }
        }
    }
}
