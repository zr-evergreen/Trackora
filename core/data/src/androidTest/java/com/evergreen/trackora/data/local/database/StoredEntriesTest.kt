package com.evergreen.trackora.data.local.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Reading every entry for a backup, against a real database opened the way
 * the app opens it: values exactly as stored, and nothing written. Each test
 * uses its own database file; synthetic data only.
 */
@RunWith(AndroidJUnit4::class)
class StoredEntriesTest {

    @get:Rule
    val testName = TestName()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var name: String
    private lateinit var database: TrackoraDatabase

    @Before
    fun setUp() {
        name = "stored-entries-${testName.methodName}"
        deleteFiles()
        database = TrackoraDatabase.build(context, name)
    }

    @After
    fun tearDown() {
        database.close()
        deleteFiles()
    }

    private fun file(suffix: String = "") = File(context.getDatabasePath(name).path + suffix)

    private fun deleteFiles() {
        context.deleteDatabase(name)
        file(".damaged").delete()
        file(".damaged-copies").deleteRecursively()
    }

    private fun sql(statement: String, vararg args: Any?) = database.openHelper.writableDatabase.execSQL(statement, args)

    private fun insert(id: Long?, quantity: Long?, title: String = "Entry", description: String? = null, date: String = "2026-11-15", photo: String? = null) =
        sql(
            "INSERT INTO work_entries (id, title, description, quantity, status, date, customField1, customField2, customField3, photoUri) " +
                "VALUES (?, ?, ?, ?, 'COMPLETED', ?, NULL, '', 'c3', ?)",
            id, title, description, quantity, date, photo,
        )

    private fun read(): Pair<List<StoredEntry>, Long?> {
        val rows = ArrayList<StoredEntry>()
        val mark = StoredEntries.read(context, database) { rows += it }
        return rows to mark
    }

    @Test
    fun anEmptyTableHasNoRowsAndNoCounter() {
        database.openHelper.writableDatabase // created, but never inserted into
        assertEquals(emptyList<StoredEntry>() to null, read())
    }

    @Test
    fun idsAndQuantitiesComeBackAs64BitValues() {
        insert(Long.MIN_VALUE, Long.MIN_VALUE)
        insert(-5, -1)
        insert(0, null)
        insert(Int.MAX_VALUE + 1L, Int.MAX_VALUE + 1L)
        insert(Long.MAX_VALUE, Long.MAX_VALUE)
        val (rows, mark) = read()
        assertEquals(listOf(Long.MIN_VALUE, -5L, 0L, Int.MAX_VALUE + 1L, Long.MAX_VALUE), rows.map { it.id })
        assertEquals(listOf(Long.MIN_VALUE, -1L, null, Int.MAX_VALUE + 1L, Long.MAX_VALUE), rows.map { it.quantity })
        assertEquals(Long.MAX_VALUE, mark)
    }

    @Test
    fun deletedIdsStayCountedSoTheyAreNeverReused() {
        repeat(5) { insert(null, it.toLong()) }
        sql("DELETE FROM work_entries WHERE id IN (2, 5)")
        val (rows, mark) = read()
        assertEquals(listOf(1L, 3L, 4L), rows.map { it.id })
        assertEquals(5L, mark)
    }

    @Test
    fun rowsComeInIdOrderWithEveryFieldAsStored() {
        val long = buildString { repeat(5_000) { append("خط $it, line $it 🧵\n") } }
        insert(30, 3, title = "", description = long, photo = "content://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg")
        insert(10, 1, title = "تعمیر لپ‌تاپ ‏مشتری‏ 👩🏽‍🔧", date = "-999999999-01-01")
        insert(20, 2, title = "\u0000\t\n\r\"\\/\u007F", description = "", photo = "")
        val (rows, _) = read()
        assertEquals(
            listOf(
                StoredEntry(10, "تعمیر لپ‌تاپ ‏مشتری‏ 👩🏽‍🔧", null, 1, "COMPLETED", "-999999999-01-01", null, "", "c3", null),
                StoredEntry(20, "\u0000\t\n\r\"\\/\u007F", "", 2, "COMPLETED", "2026-11-15", null, "", "c3", ""),
                StoredEntry(30, "", long, 3, "COMPLETED", "2026-11-15", null, "", "c3", "content://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg"),
            ),
            rows,
        )
    }

    /** SQLite would hand these back converted (text and fractions as integers, blobs as text); a backup refuses rather than change them. */
    @Test
    fun aValueStoredAsTheWrongTypeIsRefusedNotConverted() {
        val cases = listOf(
            "UPDATE work_entries SET quantity = 'twelve'" to "quantity",
            "UPDATE work_entries SET quantity = 1.5" to "quantity",
            "UPDATE work_entries SET title = X'00ff'" to "title",
            "UPDATE work_entries SET description = X'00'" to "description",
            // A number written to a text column is stored as text by SQLite itself; a blob stays a blob.
            "UPDATE work_entries SET date = X'32303236'" to "date",
        )
        for ((change, field) in cases) {
            sql("DELETE FROM work_entries")
            insert(1, 1)
            sql(change)
            try {
                read()
                fail("$change was read")
            } catch (expected: UnreadableEntryException) {
                assertTrue("${expected.message}", field in expected.message.orEmpty())
            }
        }
    }

    @Test
    fun readingWritesNothing() {
        repeat(50) { insert(null, it.toLong(), title = "Entry $it") }
        sql("DELETE FROM work_entries WHERE id = 7")
        val db = database.openHelper.writableDatabase
        val before = fingerprint()
        val changesBefore = db.query("SELECT total_changes()").use { it.moveToFirst(); it.getLong(0) }
        val dataVersionBefore = db.query("PRAGMA data_version").use { it.moveToFirst(); it.getLong(0) }

        val (rows, mark) = read()
        assertEquals(49, rows.size)
        assertEquals(50L, mark)

        assertEquals(changesBefore, db.query("SELECT total_changes()").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(dataVersionBefore, db.query("PRAGMA data_version").use { it.moveToFirst(); it.getLong(0) })
        assertEquals("the database and its -wal are byte for byte as they were", before, fingerprint())
    }

    @Test
    fun aRefusedDatabaseIsNotEvenOpened() {
        database.openHelper.writableDatabase
        insert(1, 1)
        database.close()
        file(".damaged").writeText("marker for a test")
        val fresh = TrackoraDatabase.build(context, name)
        try {
            StoredEntries.read(context, fresh) { fail("a row was read from a refused database") }
            fail("expected the read to be refused")
        } catch (expected: DatabaseRefusedException) {
        }
        assertFalse(fresh.isOpen)
        fresh.close()
    }

    @Test
    fun damageFoundWhileOpeningIsAlsoARefusal() {
        database.openHelper.writableDatabase
        insert(1, 1)
        database.close()
        // A main file whose header is no longer SQLite's: the fail-safe finds it corrupt on open.
        file().writeBytes(ByteArray(4096) { 0x55 })
        file("-wal").delete()
        file("-shm").delete()
        val damaged = TrackoraDatabase.build(context, name)
        try {
            StoredEntries.read(context, damaged) { fail("a row was read from a damaged database") }
            fail("expected the read to be refused")
        } catch (expected: DatabaseRefusedException) {
        }
        assertTrue("the fail-safe recorded the damage", file(".damaged").exists())
        damaged.close()
    }

    /** SHA-256 of the main file and the -wal (the -shm holds read marks and changes with every reader). */
    private fun fingerprint(): List<String> = listOf("", "-wal").map { suffix ->
        val f = file(suffix)
        if (!f.exists()) "$suffix absent" else "$suffix ${f.length()} " + MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    }
}
