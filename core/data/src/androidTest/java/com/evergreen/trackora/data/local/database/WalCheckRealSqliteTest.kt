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
 * case also asks SQLite itself, on a throwaway copy, what it reads: a verified
 * WAL must lose nothing committed. A refused one either does lose committed
 * rows, which is why it is refused, or holds a transaction without its commit
 * frame, which SQLite does not apply and which nothing proves was never
 * committed.
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
    private fun open(name: String, androidsOwnCheckpoints: Boolean = false): SQLiteDatabase {
        folder.mkdirs()
        val db = SQLiteDatabase.openDatabase(
            File(folder, name).path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
        )
        if (!androidsOwnCheckpoints) {
            // Inside a transaction, a query runs on the connection that writes.
            db.beginTransaction()
            try {
                pragma(db, "wal_autocheckpoint = 0")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
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

    private fun walCheck(image: Image): Result {
        folder.mkdirs()
        return WalCheck.check(File(folder, "checked-wal").apply { writeBytes(image.wal) }, pageSize(image))
    }

    /** The rows SQLite reads from [image], opened on a throwaway copy. */
    private fun rows(image: Image): Int = count(image, "SELECT count(*) FROM t")

    /** The rows SQLite reads from [image] whose value was changed from 'y' to 'z'. */
    private fun changed(image: Image): Int = count(image, "SELECT count(*) FROM t WHERE instr(v, 'z') > 0")

    private fun count(image: Image, query: String): Int {
        val copy = File(folder, "rows").apply { deleteRecursively(); mkdirs() }
        File(copy, "db").writeBytes(image.db)
        File(copy, "db-wal").writeBytes(image.wal)
        return SQLiteDatabase.openDatabase(File(copy, "db").path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use { db ->
            db.rawQuery(query, null).use { it.moveToFirst(); it.getInt(0) }
        }
    }

    /** The numbers of the commit frames in [image]'s WAL. */
    private fun commitFrames(image: Image): List<Int> = (1..frames(image)).filter { n ->
        val at = offset(image, n, 4)
        (0..3).any { image.wal[at + it].toInt() != 0 }
    }

    /**
     * Thirty rows of 3000 bytes, one to a page, in the database file itself,
     * then [commits] commits that each change one row's 'y's to 'z's in
     * place: one page, one frame each.
     *
     * The commits run on a new connection, so its page cache holds none of
     * the thirty pages: a later change must read each page from the file,
     * and a small cache then spills it into the WAL. Android's SQLite shares
     * one page cache budget between all connections of the process, so with
     * other databases left open (as earlier tests leave refused ones), pages
     * a connection wrote itself can stay cached, and a change to them never
     * spills.
     */
    private fun thirtyPages(name: String, commits: Int): SQLiteDatabase {
        val db = open(name)
        db.beginTransaction()
        try {
            repeat(30) { db.execSQL("INSERT INTO t (v) VALUES (?)", arrayOf("row $it " + "y".repeat(3000))) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        pragma(db, "wal_checkpoint(TRUNCATE)")
        db.close()
        val fresh = SQLiteDatabase.openDatabase(File(folder, name).path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
        fresh.beginTransaction()
        try {
            pragma(fresh, "wal_autocheckpoint = 0")
            fresh.setTransactionSuccessful()
        } finally {
            fresh.endTransaction()
        }
        repeat(commits) { fresh.execSQL("UPDATE t SET v = replace(v, 'y', 'z') WHERE id = ${it + 1}") }
        return fresh
    }

    private fun resource(path: String): ByteArray = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }

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

    /** Forty entries, each committed on its own. */
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

    /**
     * Killed in the middle of a transaction that spilled pages into the WAL,
     * some of them more than once. SQLite 3.9 (Android 7) only appends such
     * frames; 3.11 and later also rewrite one in place and fix its checksum
     * only at commit. Either way the WAL does not end on a commit: refused.
     * SQLite reads every committed row.
     */
    @Test
    fun anUnfinishedTransactionAfterTheCommitsIsRefused() {
        val db = open("unfinished")
        insert(db, 20)
        db.beginTransaction()
        try {
            smallCache(db, 2)
            insert(db, 200, size = 3000) // spills uncommitted frames into the WAL
            val image = image("unfinished")
            val result = walCheck(image)
            assertTrue("Expected Unverified, was $result", result is Result.Unverified)
            assertEquals("SQLite lost committed rows", 20, rows(image))
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /**
     * A: killed in the middle of a transaction that spilled its pages into
     * the WAL, each page once, so every frame is valid and none is a commit.
     * SQLite applies only the three commits before it.
     */
    @Test
    fun anInterruptedSpilledTransactionIsRefused() {
        val db = thirtyPages("interrupted", commits = 3)
        db.beginTransaction()
        try {
            smallCache(db, 2)
            db.execSQL("UPDATE t SET v = replace(v, 'y', 'z') WHERE id > 3")
            val image = image("interrupted")
            val result = walCheck(image)
            assertTrue("Expected Unverified, was $result", result is Result.Unverified)
            assertTrue("No frames after the commits", frames(image) > commitFrames(image).last())
            assertEquals(30, rows(image))
            assertEquals("SQLite applied uncommitted changes", 3, changed(image))
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    /** B: the same transaction rolled back, with nothing written after it: its frames stay in the WAL. */
    @Test
    fun aSpilledTransactionRolledBackWithNothingWrittenAfterIsRefused() {
        val db = thirtyPages("rolledback-alone", commits = 3)
        db.beginTransaction()
        try {
            smallCache(db, 2)
            db.execSQL("UPDATE t SET v = replace(v, 'y', 'z') WHERE id > 3")
        } finally {
            db.endTransaction()
        }
        val image = image("rolledback-alone")
        db.close()
        val result = walCheck(image)
        assertTrue("Expected Unverified, was $result", result is Result.Unverified)
        assertEquals(30, rows(image))
        assertEquals(3, changed(image))
    }

    /**
     * C1: a commit of five pages, cut just before its commit frame, as a kill
     * between its frame writes leaves it, or a cut that took the commit frame.
     * SQLite applies nothing of it; refused, because nothing tells which.
     */
    @Test
    fun aCommitCutBeforeItsCommitFrameIsRefused() {
        val db = thirtyPages("cut-commit", commits = 2)
        db.execSQL("UPDATE t SET v = replace(v, 'y', 'z') WHERE id BETWEEN 10 AND 14")
        val full = image("cut-commit")
        db.close()
        assertTrue(walCheck(full) is Result.Verified)
        assertEquals(7, changed(full))
        val cut = full.wal { it.copyOf(offset(full, frames(full))) }
        val result = walCheck(cut)
        assertTrue("Expected Unverified, was $result", result is Result.Unverified)
        assertEquals("SQLite applied part of a commit", 2, changed(cut))
    }

    /**
     * C2, the limit no check of the file can pass: the same commit cut before
     * its first frame reads as a shorter, sound WAL. Verified, and SQLite has
     * lost the commit. Kept as a test so the limit stays visible.
     */
    @Test
    fun aCommitCutBeforeItsFirstFrameCannotBeTold() {
        val db = thirtyPages("cut-before", commits = 2)
        val before = frames(image("cut-before"))
        db.execSQL("UPDATE t SET v = replace(v, 'y', 'z') WHERE id BETWEEN 10 AND 14")
        val full = image("cut-before")
        db.close()
        val cut = full.wal { it.copyOf(offset(full, before + 1)) }
        assertTrue(walCheck(cut) is Result.Verified)
        assertEquals(2, changed(cut))
    }

    /**
     * D4: Android's own automatic checkpoints, every 100 frames, reset the
     * WAL again and again while the app commits, leaving leftovers behind its
     * last commit: verified, and nothing lost.
     */
    @Test
    fun androidsOwnCheckpointsLeaveAWalThatIsVerified() {
        val db = open("auto", androidsOwnCheckpoints = true)
        insert(db, 350)
        val image = image("auto")
        db.close()
        val result = assertVerified(image, 350, "350 commits under Android's own checkpoints")
        assertTrue("No reset happened: $result", result.leftoverFrames > 0)
    }

    /**
     * Commits under synchronous=FULL, which Room's own connection uses on some
     * Android versions (API 24, 26 and 33 among those tested). A commit that
     * syncs is where SQLite writes padding on storage without powersafe
     * overwrite; Android's SQLite keeps it on and writes none.
     */
    @Test
    fun commitsUnderFullSyncAreVerified() {
        val db = open("fullsync")
        db.execSQL("PRAGMA synchronous = FULL")
        insert(db, 5)
        val image = image("fullsync")
        db.close()
        assertVerified(image, 5, "synchronous=FULL")
    }

    /**
     * Padding another SQLite wrote after each commit (the laptop's 3.53, with
     * powersafe overwrite off and synchronous=FULL; see WalCheckTest): this
     * device's SQLite applies the padding frames as commits, and the check
     * accepts the WAL whole and cut anywhere within a commit's padding.
     */
    @Test
    fun paddingAfterACommitIsAppliedAndVerified() {
        val image = Image(resource("/wal/padded.db"), resource("/wal/padded.wal"))
        assertEquals(Result.Verified(16, 16, 16, 0, 0), walCheck(image))
        assertEquals(1, rows(image))
        val inSecondPadding = image.wal { it.copyOf(offset(image, 13)) }
        assertEquals(Result.Verified(12, 12, 12, 0, 0), walCheck(inSecondPadding))
        assertEquals(1, rows(inSecondPadding))
        val inFirstPadding = image.wal { it.copyOf(offset(image, 6)) }
        assertEquals(Result.Verified(5, 5, 5, 0, 0), walCheck(inFirstPadding))
        assertEquals(0, rows(inFirstPadding))
        // Cut after the entry's first frame, before its commit frame.
        val beforeCommit = image.wal { it.copyOf(offset(image, 10)) }
        assertTrue(walCheck(beforeCommit) is Result.Unverified)
        assertEquals(0, rows(beforeCommit))
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
     * The limit no check of the file can pass: cut exactly after a commit
     * frame, the WAL reads as a shorter, sound one, and SQLite reads fewer
     * rows. Kept as a test so the limit stays visible.
     */
    @Test
    fun aWalCutAtAFrameBoundaryCannotBeTold() {
        val image = forty()
        val lastKept = commitFrames(image).last { it <= frames(image) - 4 }
        val cut = image.wal { it.copyOf(offset(image, lastKept + 1)) }
        assertTrue(walCheck(cut) is Result.Verified)
        assertTrue(rows(cut) < 40)
    }
}
