package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.data.local.database.WalCheck.Result
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [WalCheck] on WAL files written by this device's own SQLite, each set of
 * files copied while the connection was open, as a crash leaves them. Every
 * case also asks SQLite itself, on a throwaway copy, how many rows it reads:
 * a verified WAL must lose none, and each refused damaged one does lose rows,
 * which is why it is refused.
 */
@RunWith(AndroidJUnit4::class)
class WalCheckRealSqliteTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val folder: File get() = File(context.noBackupFilesDir, "walcheck-test")

    @Before
    @After
    fun deleteTestFiles() {
        folder.deleteRecursively()
    }

    private class Image(val db: ByteArray, val wal: ByteArray)

    /**
     * A WAL database, opened as Android opens one (its journal_size_limit is
     * Android's own, 512 KiB), with automatic checkpoints off on the
     * connection that writes (Android's own is every 100 frames on some
     * versions), so each test decides when the WAL is reset.
     */
    private fun open(name: String): SQLiteDatabase {
        folder.mkdirs()
        val db = SQLiteDatabase.openDatabase(
            File(folder, name).path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        )
        // Inside a transaction, a query runs on the connection that writes.
        db.beginTransaction()
        try {
            pragma(db, "wal_autocheckpoint = 0")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS t (id INTEGER PRIMARY KEY, v TEXT)")
        // The table in the database file itself, so every later frame is an entry.
        pragma(db, "wal_checkpoint(TRUNCATE)")
        return db
    }

    /** A PRAGMA that returns a row; it may run on any of the pool's connections. */
    private fun pragma(db: SQLiteDatabase, sql: String) = db.rawQuery("PRAGMA $sql", null).use { it.moveToFirst() }

    /** On the connection that writes: a tiny page cache, so a transaction spills its pages into the WAL before it ends. */
    private fun smallCache(db: SQLiteDatabase, pages: Int) = db.execSQL("PRAGMA cache_size = $pages")

    private fun insert(db: SQLiteDatabase, count: Int, size: Int = 300) =
        repeat(count) { db.execSQL("INSERT INTO t (v) VALUES (?)", arrayOf("entry $it " + "y".repeat(size))) }

    private fun image(name: String) = Image(File(folder, name).readBytes(), File(folder, "$name-wal").readBytes())

    private fun pageSize(image: Image) = ((image.db[16].toInt() and 0xff) shl 8) or (image.db[17].toInt() and 0xff)

    private fun walCheck(image: Image): Result =
        WalCheck.check(File(folder, "checked-wal").apply { writeBytes(image.wal) }, pageSize(image))

    /** The rows SQLite reads from [image], opened on a throwaway copy. */
    private fun rows(image: Image): Int {
        val copy = File(folder, "rows").apply { deleteRecursively(); mkdirs() }
        File(copy, "db").writeBytes(image.db)
        File(copy, "db-wal").writeBytes(image.wal)
        return SQLiteDatabase.openDatabase(File(copy, "db").path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { db ->
            db.rawQuery("SELECT count(*) FROM t", null).use { it.moveToFirst(); it.getInt(0) }
        }
    }

    private fun frameSize(image: Image) = 24 + pageSize(image)
    private fun frames(image: Image) = (image.wal.size - 32) / frameSize(image)
    private fun offset(image: Image, frame: Int, at: Int = 0) = 32 + (frame - 1) * frameSize(image) + at

    private fun Image.wal(change: (ByteArray) -> ByteArray) = Image(db, change(wal.copyOf()))
    private fun ByteArray.flip(at: Int) = also { it[at] = (it[at].toInt() xor 0x5A).toByte() }

    private fun assertVerified(image: Image, expectedRows: Int, label: String): Result.Verified {
        val result = walCheck(image)
        assertTrue("$label: expected Verified, was $result", result is Result.Verified)
        assertEquals("$label: SQLite lost rows from a verified WAL", expectedRows, rows(image))
        return result as Result.Verified
    }

    private fun assertRefusedAndLosing(image: Image, committedRows: Int, label: String) {
        val result = walCheck(image)
        assertTrue("$label: expected Unverified, was $result", result is Result.Unverified)
        val read = try { rows(image) } catch (e: Exception) { -1 }
        assertTrue("$label: SQLite still reads all $committedRows rows ($read), so this is not a loss", read < committedRows)
    }

    /** Forty entries, each committed on its own: one frame each. */
    private fun forty(): Image {
        val db = open("forty")
        insert(db, 40)
        return image("forty").also { db.close() }
    }

    @Test
    fun aWalWithManyCommitsIsVerifiedAndLosesNothing() {
        val image = forty()
        val result = assertVerified(image, 40, "40 commits")
        assertEquals(frames(image), result.validFrames)
        assertEquals(result.validFrames, result.committedFrames)
    }

    @Test
    fun anUnfinishedTransactionAfterTheCommitsIsVerified() {
        val db = open("unfinished")
        insert(db, 20)
        db.beginTransaction()
        try {
            smallCache(db, 2)
            insert(db, 200, size = 3000) // spills uncommitted frames into the WAL
            val image = image("unfinished")
            val result = assertVerified(image, 20, "unfinished transaction")
            assertTrue("No uncommitted frames: $result", result.validFrames > result.committedFrames)
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    @Test
    fun leftoversFromBeforeAResetAreVerified() {
        val db = open("reset")
        insert(db, 100)
        pragma(db, "wal_checkpoint(RESTART)")
        insert(db, 2)
        val image = image("reset")
        db.close()
        val result = assertVerified(image, 102, "after a reset")
        assertEquals(2, result.validFrames)
        assertTrue("No leftovers: $result", result.leftoverFrames > 50)
    }

    @Test
    fun aWalCutByItsSizeLimitAfterAResetIsVerified() {
        val db = open("limit")
        insert(db, 300)
        pragma(db, "wal_checkpoint(RESTART)")
        insert(db, 2)
        val image = image("limit")
        db.close()
        assertEquals("The WAL was not cut to its size limit", 524_288, image.wal.size)
        val result = assertVerified(image, 302, "cut to the size limit")
        assertTrue("No partial frame: $result", result.partialBytes > 0)
    }

    /** Sound, and SQLite loses nothing, but the rolled-back frames cannot be proven uncommitted: refused, by design. */
    @Test
    fun framesOfATransactionRolledBackAfterSpillingAreRefused() {
        val db = open("rolledback")
        insert(db, 10)
        db.beginTransaction()
        try {
            smallCache(db, 2)
            insert(db, 100, size = 3000)
        } finally {
            db.endTransaction()
        }
        smallCache(db, 2000)
        insert(db, 1)
        val image = image("rolledback")
        db.close()
        assertTrue(walCheck(image) is Result.Unverified)
        assertEquals("SQLite itself reads every committed row", 11, rows(image))
    }

    @Test
    fun aDamagedPageByteIsRefused() {
        val image = forty()
        assertRefusedAndLosing(image.wal { it.flip(offset(image, 10, 24 + 1000)) }, 40, "page byte of frame 10")
    }

    @Test
    fun aDamagedSaltIsRefused() {
        val image = forty()
        assertRefusedAndLosing(image.wal { it.flip(offset(image, 10, 8)) }, 40, "salt of frame 10")
    }

    @Test
    fun aDamagedFrameAfterValidCommitsIsRefused() {
        val image = forty()
        val last = frames(image)
        assertRefusedAndLosing(image.wal { it.flip(offset(image, last - 3, 24 + 77)) }, 40, "frame ${last - 3} of $last")
    }

    @Test
    fun theLastCommitDamagedIsRefused() {
        val image = forty()
        val last = frames(image)
        assertRefusedAndLosing(image.wal { it.flip(offset(image, last, 24 + 500)) }, 40, "page byte of the last commit")
        assertRefusedAndLosing(image.wal { it.also { w -> w.fill(0, offset(image, last), offset(image, last, 24)) } }, 40, "header of the last commit zeroed")
    }

    @Test
    fun aFrameZeroedEntirelyIsRefused() {
        val image = forty()
        assertRefusedAndLosing(image.wal { it.also { w -> w.fill(0, offset(image, 10), offset(image, 11)) } }, 40, "frame 10 zeroed")
    }

    @Test
    fun aWalCutPartwayThroughAFrameIsRefused() {
        val image = forty()
        assertRefusedAndLosing(image.wal { it.copyOf(offset(image, frames(image) - 4, 1000)) }, 40, "cut inside a frame")
    }

    @Test
    fun aHeaderChecksumThatDoesNotMatchIsRefused() {
        val image = forty()
        assertRefusedAndLosing(image.wal { it.flip(12) }, 40, "header checksum")
    }

    /**
     * The limit no check of the file can pass: cut exactly at a frame
     * boundary, the WAL reads as a shorter, sound one, and SQLite reads fewer
     * rows. Kept as a test so the limit stays visible.
     */
    @Test
    fun aWalCutAtAFrameBoundaryCannotBeTold() {
        val image = forty()
        val cut = image.wal { it.copyOf(offset(image, frames(image) - 4)) }
        assertTrue(walCheck(cut) is Result.Verified)
        assertTrue(rows(cut) < 40)
    }
}
