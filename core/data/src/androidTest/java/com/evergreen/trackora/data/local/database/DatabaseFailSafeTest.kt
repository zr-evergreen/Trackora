package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.data.local.entity.WorkEntry
import com.evergreen.trackora.domain.model.Status
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate

/**
 * What becomes of the database when SQLite reports a storage error or
 * corruption: it is never deleted, never replaced by an empty one, and a
 * corrupt one is kept, copied aside and refused. Its `-wal` and `-shm` are kept
 * and copied too when Room's schema check or a query finds the damage. When
 * Android's own connection setup finds it, Android may already have merged a
 * live `-wal` into the file (see [FailSafeOpenHelperFactory]); no test here
 * covers that case.
 *
 * Each protection has a control beside it, run without the fail-safe, which
 * shows the same situation deleting the database. That is what proves the
 * test reaches the dangerous path at all. If a control ever fails,
 * androidx.sqlite no longer deletes in that case, and
 * [FailSafeOpenHelperFactory] can be revisited.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseFailSafeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "fail-safe-test"
    private val file: File get() = context.getDatabasePath(name)
    private val marker: File get() = File(file.path + DAMAGED_SUFFIX)

    @After
    fun deleteTestFiles() {
        context.deleteDatabase(name)
        marker.delete()
        copies().forEach { it.deleteRecursively() }
    }

    private fun copies(): List<File> =
        file.parentFile?.listFiles { f -> f.isDirectory && f.name.startsWith(name + DAMAGED_SUFFIX + "-") }
            ?.toList().orEmpty()

    private fun bytes(suffix: String): ByteArray? = File(file.path + suffix).takeIf { it.exists() }?.readBytes()

    private fun overwrite(offset: Long, bytes: ByteArray) =
        RandomAccessFile(file, "rw").use { it.seek(offset); it.write(bytes) }

    private fun Throwable.chain() = generateSequence(this) { it.cause }

    private fun entry(i: Int) = WorkEntry(
        title = "Entry $i " + "پیراهن آبی ".repeat(8),
        quantity = i,
        status = Status.COMPLETED,
        date = LocalDate.of(2026, 1, 1).plusDays(i.toLong()),
    )

    /** A database at the current version with [count] entries, written and closed by the app's own builder. */
    private fun createWithEntries(count: Int) = runBlocking {
        val database = TrackoraDatabase.build(context, name)
        try {
            repeat(count) { database.workEntryDao().insertEntry(entry(it + 1)) }
        } finally {
            database.close()
        }
    }

    private fun readOnly(query: String): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(query, null).use { it.moveToFirst(); it.getInt(0) }
        }

    /**
     * The files as a crash leaves them: [unsaved] more entries committed to the
     * `-wal` and never checkpointed into the database file, because the app
     * was killed instead of closing it.
     */
    private fun crashImage(unsaved: Int): Map<String, ByteArray> = runBlocking {
        val database = TrackoraDatabase.build(context, name)
        val image = try {
            repeat(unsaved) { database.workEntryDao().insertEntry(entry(1_000 + it)) }
            listOf("", "-wal", "-shm").associateWith { bytes(it)!! }
        } finally {
            database.close()
        }
        context.deleteDatabase(name)
        image.forEach { (suffix, content) -> File(file.path + suffix).writeBytes(content) }
        image
    }

    // ---- Storage errors ------------------------------------------------------

    /** A plain table of [rows] rows, at version 1. */
    private fun createPlain(rows: Int) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE t (x INTEGER)")
            repeat(rows) { db.execSQL("INSERT INTO t VALUES ($it)") }
            db.version = 1
        }
    }

    /**
     * A callback whose open check fails with [failure] while [fails], as
     * failing storage can make it: Room's check reads the database there.
     */
    private class FlakyStorage(private val failure: SQLiteException) : SupportSQLiteOpenHelper.Callback(1) {
        @Volatile var fails = true
        @Volatile var failures = 0
        override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL("CREATE TABLE t (x INTEGER)")
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        override fun onOpen(db: SupportSQLiteDatabase) {
            if (fails) {
                failures++
                throw failure
            }
        }
    }

    private fun configuration(callback: SupportSQLiteOpenHelper.Callback) =
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()

    private val storageErrors = listOf(
        SQLiteDiskIOException("disk I/O error (simulated)"),
        SQLiteFullException("database or disk is full (simulated)"),
        SQLiteDatabaseLockedException("database is locked (simulated)"),
        SQLiteCantOpenDatabaseException("unable to open database file (simulated)"),
    )

    @Test
    fun aStorageErrorWhileOpeningNeverDeletesTheDatabase() {
        createPlain(rows = 3)

        for (failure in storageErrors) {
            val storage = FlakyStorage(failure)
            val helper = FailSafeOpenHelperFactory().create(configuration(storage))
            try {
                val error = assertThrows(DatabaseOpenException::class.java) { helper.writableDatabase }
                assertSame(failure, error.cause)
                // Tried again, which is what leads androidx.sqlite to delete.
                assertTrue("Opened only ${storage.failures} time(s)", storage.failures >= 2)
                assertEquals("After $failure", 3, readOnly("SELECT count(*) FROM t"))

                // Once the storage recovers, the next attempt opens it as it was.
                storage.fails = false
                val rows = helper.writableDatabase.query("SELECT count(*) FROM t").use { it.moveToFirst(); it.getInt(0) }
                assertEquals(3, rows)
            } finally {
                helper.close()
            }
        }

        assertFalse("A storage error is not corruption", marker.exists())
        assertTrue(copies().isEmpty())
    }

    @Test
    fun controlWithoutTheFailSafeAStorageErrorWhileOpeningDeletesTheDatabase() {
        createPlain(rows = 3)

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration(FlakyStorage(storageErrors.first())))
        runCatching { helper.writableDatabase }
        helper.close()

        // Deleted and created again, empty.
        assertEquals(0, readOnly("SELECT count(*) FROM t"))
    }

    @Test
    fun recoveryByDeletingStaysOffEvenWhenAskedFor() {
        var passedOn: SupportSQLiteOpenHelper.Configuration? = null
        val recording = object : SupportSQLiteOpenHelper.Factory {
            override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
                passedOn = configuration
                return FrameworkSQLiteOpenHelperFactory().create(configuration)
            }
        }

        FailSafeOpenHelperFactory(recording).create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(FlakyStorage(storageErrors.first()))
                .allowDataLossOnRecovery(true)
                .build()
        ).close()

        assertFalse(passedOn!!.allowDataLossOnRecovery)
        assertEquals(name, passedOn!!.name)
    }

    // ---- Corruption reported while opening -----------------------------------

    @Test
    fun aDatabaseFoundCorruptWhenOpeningIsKeptCopiedAndRefused() = runBlocking {
        createWithEntries(3)
        // An unreadable header: SQLite reports "file is not a database" as it opens.
        // The database was closed cleanly, so there is no -wal. With a live one,
        // Android would merge it into the file before the copy is made.
        overwrite(0, ByteArray(100) { 0x42 })
        val original = bytes("")!!

        val database = TrackoraDatabase.build(context, name)
        try {
            database.workEntryDao().getAllEntries().first()
            fail("Opened a corrupt database")
        } catch (e: DatabaseDamagedException) {
            assertTrue(e.chain().any { it is SQLiteDatabaseCorruptException })
        }
        // Refused from then on, without being opened again.
        assertThrows(DatabaseDamagedException::class.java) { database.openHelper.writableDatabase }
        database.close()

        assertArrayEquals("The database file was changed", original, bytes(""))
        assertTrue("No marker beside the database", marker.exists())
        val copy = copies().single()
        assertArrayEquals(original, File(copy, file.name).readBytes())

        // And at the next launch.
        val again = TrackoraDatabase.build(context, name)
        assertThrows(DatabaseDamagedException::class.java) { again.openHelper.writableDatabase }
        again.close()
        assertArrayEquals(original, bytes(""))
        assertEquals("Copied again at the next launch", 1, copies().size)
    }

    @Test
    fun controlWithoutTheFailSafeACorruptDatabaseIsSilentlyReplacedByAnEmptyOne() = runBlocking {
        createWithEntries(3)
        overwrite(0, ByteArray(100) { 0x42 })

        val plain = Room.databaseBuilder(context, TrackoraDatabase::class.java, name)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
        try {
            assertEquals(emptyList<WorkEntry>(), plain.workEntryDao().getAllEntries().first())
        } finally {
            plain.close()
        }
    }

    // ---- Corruption found while reading --------------------------------------

    @Test
    fun corruptionFoundWhileReadingStopsAllUseAndKeepsTheWalAndShm() = runBlocking {
        createWithEntries(200)
        val root = readOnly("SELECT rootpage FROM sqlite_master WHERE name = 'work_entries'")
        val pageSize = readOnly("PRAGMA page_size")
        // The entries table's first page unreadable. The rest of the file is
        // fine, so the database opens and the damage is found on reading.
        overwrite((root - 1L) * pageSize, ByteArray(pageSize) { 0xAB.toByte() })

        val database = TrackoraDatabase.build(context, name)
        // A change committed to the -wal and not yet checkpointed, as in a
        // running app. A real change: SQLite 3.23+ writes nothing for an
        // update to the same value.
        database.openHelper.writableDatabase.execSQL("UPDATE sqlite_sequence SET seq = seq + 1 WHERE name = 'work_entries'")
        val main = bytes("")!!
        val wal = bytes("-wal")!!
        assertTrue("The test needs frames in the -wal", wal.isNotEmpty())
        assertNotNull("The test needs an -shm", bytes("-shm"))

        // The read that finds the damage fails with SQLite's own error.
        val error = runCatching { database.workEntryDao().getEntriesByStatus(Status.COMPLETED.name) }.exceptionOrNull()
        assertNotNull("Read a corrupt table", error)
        assertTrue(
            "Not reported as corruption: ${error!!.chain().toList()}\n${error.stackTraceToString()}",
            error.chain().any { it is SQLiteDatabaseCorruptException }
        )

        // Nothing touches the files after that: no further use, and no
        // closing, whose checkpoint would write the -wal into the database.
        // A Flow, as the screens use, is refused too.
        assertThrows(DatabaseDamagedException::class.java) {
            runBlocking { database.workEntryDao().getAllEntries().first() }
        }
        assertThrows(DatabaseDamagedException::class.java) {
            runBlocking { database.workEntryDao().insertEntry(entry(999)) }
        }
        assertThrows(DatabaseDamagedException::class.java) { database.openHelper.writableDatabase }
        database.close()
        assertArrayEquals("The database file was changed", main, bytes(""))
        assertArrayEquals("The -wal was changed or checkpointed", wal, bytes("-wal"))
        assertNotNull("The -shm was deleted", bytes("-shm"))
        assertTrue("No marker beside the database", marker.exists())

        // The files as they were are copied aside, -wal and -shm included.
        val copy = copies().single()
        assertArrayEquals(main, File(copy, file.name).readBytes())
        assertArrayEquals(wal, File(copy, file.name + "-wal").readBytes())
        assertTrue(File(copy, file.name + "-shm").exists())

        // And the next launch refuses it before opening it.
        val again = TrackoraDatabase.build(context, name)
        assertThrows(DatabaseDamagedException::class.java) { again.openHelper.writableDatabase }
        again.close()
        assertArrayEquals(main, bytes(""))
        assertArrayEquals(wal, bytes("-wal"))
    }

    // ---- After a crash: changes only in the -wal ------------------------------

    @Test
    fun corruptionFoundWhileOpeningAfterACrashLosesNoCommittedChange() = runBlocking {
        createWithEntries(200)
        val root = readOnly("SELECT rootpage FROM sqlite_master WHERE name = 'room_master_table'")
        val pageSize = readOnly("PRAGMA page_size")
        val image = crashImage(unsaved = 5)
        assertTrue("The test needs frames in the -wal", image.getValue("-wal").isNotEmpty())
        // Room's own table unreadable: the schema check that ends opening finds it.
        overwrite((root - 1L) * pageSize, ByteArray(pageSize) { 0xAB.toByte() })
        val main = bytes("")!!

        val database = TrackoraDatabase.build(context, name)
        assertThrows(DatabaseDamagedException::class.java) {
            runBlocking { database.workEntryDao().getAllEntries().first() }
        }
        database.close()
        assertTrue("No marker beside the database", marker.exists())

        // Room's schema check found the damage, so the files exactly as they
        // were, -wal and -shm included, are copied aside before Android closes
        // the failed connection. Had Android's connection setup found it, the
        // -wal would already have been merged.
        val copy = copies().single()
        assertArrayEquals(main, File(copy, file.name).readBytes())
        assertArrayEquals(image.getValue("-wal"), File(copy, file.name + "-wal").readBytes())
        // The -shm is an index SQLite rebuilds from the -wal as it opens.
        assertTrue(File(copy, file.name + "-shm").exists())

        Log.i(
            "DatabaseFailSafeTest",
            "In place after the failed open: -wal ${bytes("-wal")?.size} bytes (was ${image.getValue("-wal").size}), " +
                "database file changed: ${!main.contentEquals(bytes("")!!)}"
        )
        // Whether or not that close checkpointed the -wal, every committed
        // entry is still in the files in place.
        assertEquals(205, readOnly("SELECT count(*) FROM work_entries"))
    }

    @Test
    fun aWalWithoutItsDatabaseIsKeptCopiedAndRefused() = runBlocking {
        createWithEntries(3)
        val wal = crashImage(unsaved = 2).getValue("-wal")
        // The database file emptied, as failing storage can leave it. Only the
        // -wal holds anything now.
        RandomAccessFile(file, "rw").use { it.setLength(0) }

        val database = TrackoraDatabase.build(context, name)
        assertThrows(DatabaseDamagedException::class.java) {
            runBlocking { database.workEntryDao().getAllEntries().first() }
        }
        database.close()

        assertEquals("The database file was written", 0L, file.length())
        assertArrayEquals("The -wal was changed or deleted", wal, bytes("-wal"))
        assertTrue("No marker beside the database", marker.exists())
        assertArrayEquals(wal, File(copies().single(), file.name + "-wal").readBytes())
    }

    @Test
    fun controlWithoutTheFailSafeAWalWithoutItsDatabaseIsLostToAnEmptyDatabase() = runBlocking {
        createWithEntries(3)
        val wal = crashImage(unsaved = 2).getValue("-wal")
        RandomAccessFile(file, "rw").use { it.setLength(0) }

        val plain = Room.databaseBuilder(context, TrackoraDatabase::class.java, name)
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
        try {
            assertEquals(emptyList<WorkEntry>(), plain.workEntryDao().getAllEntries().first())
            assertFalse("The -wal survived", wal.contentEquals(bytes("-wal") ?: ByteArray(0)))
        } finally {
            plain.close()
        }
    }

    /** The check above must never take a new install that crashed for damage. */
    @Test
    fun aCrashRightAfterTheFirstEntriesOfANewInstallLosesNothing() = runBlocking {
        val image = crashImage(unsaved = 2)
        assertTrue("The database file is empty", image.getValue("").isNotEmpty())
        assertTrue("The test needs frames in the -wal", image.getValue("-wal").isNotEmpty())

        val database = TrackoraDatabase.build(context, name)
        try {
            assertEquals(2, database.workEntryDao().getAllEntries().first().size)
        } finally {
            database.close()
        }

        assertFalse(marker.exists())
        assertTrue(copies().isEmpty())
    }

    // ---- Nothing else changes ------------------------------------------------

    @Test
    fun aHealthyDatabaseOpensAsBeforeAndLeavesNothingBehind() = runBlocking {
        createWithEntries(3)

        val database = TrackoraDatabase.build(context, name)
        try {
            assertEquals(3, database.workEntryDao().getAllEntries().first().size)
        } finally {
            database.close()
        }

        assertFalse(marker.exists())
        assertTrue(copies().isEmpty())
    }
}
