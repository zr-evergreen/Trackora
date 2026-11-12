package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.evergreen.trackora.data.local.database.DatabaseRecheck.Outcome
import com.evergreen.trackora.data.local.database.DatabaseRecheck.Reason
import com.evergreen.trackora.data.local.entity.WorkEntry
import com.evergreen.trackora.domain.model.Status
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * The whole way from damage to recovery with the real fail-safe: the
 * database is built and opened by the app's own [TrackoraDatabase.build],
 * the marker and copies are the ones [FailSafeOpenHelperFactory] writes when
 * SQLite reports corruption, and "a new process" is one: [FreshProcessProbe]
 * runs in a process of its own.
 */
@RunWith(AndroidJUnit4::class)
class RealMarkerRecheckTest {

    @get:Rule
    val test = TestName()

    private val context: Context = ApplicationProvider.getApplicationContext()

    // One database for each test: a refusal holds for the rest of the process, for its file.
    private val name: String get() = "realmarker-" + test.methodName.take(24)
    private val file: File get() = context.getDatabasePath(name)
    private val marker: File get() = File(file.path + DAMAGED_SUFFIX)
    private val copiesFolder: File get() = File(file.path + DAMAGED_COPIES_SUFFIX)
    private val work: File get() = File(context.noBackupFilesDir, "realmarker-test-work")

    @Before
    @After
    fun deleteTestFiles() {
        file.parentFile?.listFiles { f -> f.name.startsWith(name) }?.forEach { it.deleteRecursively() }
        work.deleteRecursively()
    }

    private fun entry(i: Int) = WorkEntry(
        title = "Entry $i " + "پیراهن آبی ".repeat(8),
        quantity = i,
        status = Status.COMPLETED,
        date = LocalDate.of(2026, 1, 1).plusDays(i.toLong()),
    )

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

    private fun overwrite(offset: Long, bytes: ByteArray) =
        RandomAccessFile(file, "rw").use { it.seek(offset); it.write(bytes) }

    private fun read(offset: Long, size: Int): ByteArray =
        RandomAccessFile(file, "r").use { f -> ByteArray(size).also { f.seek(offset); f.readFully(it) } }

    private fun liveFiles(): Map<String, List<Byte>> =
        file.parentFile!!.listFiles { f -> f.isFile && (f.name == name || f.name.startsWith("$name-")) }!!
            .associate { it.name to it.readBytes().toList() }

    private fun contents(folder: File): Map<String, List<Byte>> =
        folder.walkTopDown().filter { it.isFile }.associate { it.relativeTo(folder).path to it.readBytes().toList() }

    private class Fresh(val pid: Int, val titles: List<String>?, val error: String?)

    /** Opens the database in a new process, then ends that process. */
    private fun inANewProcess(): Fresh {
        val reply = context.contentResolver.acquireUnstableContentProviderClient("${context.packageName}.freshprocess")!!
            .use { it.call(FreshProcessProbe.OPEN, name, null)!! }
        val pid = reply.getInt(FreshProcessProbe.PID)
        Process.killProcess(pid)
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (File("/proc/$pid").exists() && SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
        assertFalse("The new process $pid did not end", File("/proc/$pid").exists())
        return Fresh(pid, reply.getStringArrayList(FreshProcessProbe.TITLES), reply.getString(FreshProcessProbe.ERROR))
    }

    /**
     * Damage first found during a session, while the app is using the
     * database: signalled at once, kept, refused in every process, checked
     * again without anything being changed, and lifted only once the page
     * reads back sound, after which a new process opens every entry.
     */
    @Test
    fun damageFoundDuringASessionIsRecordedRefusedAndLiftedOnlyOnceSound() = runBlocking {
        createWithEntries(200)
        val root = readOnly("SELECT rootpage FROM sqlite_master WHERE name = 'work_entries'")
        val pageSize = readOnly("PRAGMA page_size")
        val rootOffset = (root - 1L) * pageSize
        val sound = read(rootOffset, pageSize)
        // The entries table's first page unreadable; the rest of the file is fine.
        overwrite(rootOffset, ByteArray(pageSize) { 0xAB.toByte() })
        assertFalse(marker.exists())

        val session = TrackoraDatabase.build(context, name)
        val refusal = DatabaseDamage.refusal(file)
        val otherDatabase = DatabaseDamage.refusal(File(file.parentFile, "$name-other"))
        assertFalse("Refused before anything was found", refusal.value)
        // The session is under way: the database opens and takes a change,
        // which stays in the -wal, as in a running app.
        session.openHelper.writableDatabase.execSQL("UPDATE sqlite_sequence SET seq = seq + 1 WHERE name = 'work_entries'")
        assertFalse(refusal.value)

        // The first read of the entries finds the damage.
        assertNotNull(runCatching { session.workEntryDao().getEntriesByStatus(Status.COMPLETED.name) }.exceptionOrNull())

        // Signalled at once, in this process, with no new instance and no restart.
        assertTrue("Not signalled", refusal.value)
        assertFalse("Signalled for another database", otherDatabase.value)
        // The real fail-safe's marker and copies.
        assertTrue(marker.exists())
        val markerBytes = marker.readBytes()
        assertTrue(String(markerBytes), String(markerBytes).startsWith("SQLite reported it as corrupt"))
        val failSafeCopies = contents(copiesFolder)
        assertTrue(failSafeCopies.keys.toString(), failSafeCopies.keys.any { it.endsWith("/$name") })
        assertThrows(DatabaseDamagedException::class.java) { session.openHelper.writableDatabase }
        val damaged = liveFiles()
        assertTrue(damaged.keys.containsAll(listOf(name, "$name-wal", "$name-shm")))

        // A new process refuses it before opening it, and changes nothing.
        val first = inANewProcess()
        assertNotEquals(Process.myPid(), first.pid)
        assertTrue("Opened in a new process: ${first.error}", first.error.orEmpty().contains("DatabaseDamagedException"))
        assertEquals("The new process changed the files", damaged, liveFiles())

        // Checked again while still damaged, with the session's refused
        // instance still open in this process: nothing is changed.
        val stillDamaged = DatabaseRecheck(file, work).run()
        assertEquals(stillDamaged.toString(), Reason.DAMAGED, (stillDamaged as? Outcome.StillRefused)?.reason)
        assertEquals(damaged, liveFiles())
        assertArrayEquals(markerBytes, marker.readBytes())
        assertEquals(failSafeCopies, contents(copiesFolder))

        // A false report: the page reads back as it was written.
        overwrite(rootOffset, sound)
        val repaired = liveFiles()
        val cleared = DatabaseRecheck(file, work).run()
        assertTrue(cleared.toString(), cleared is Outcome.Cleared)
        cleared as Outcome.Cleared
        assertEquals(2, cleared.schemaVersion)
        assertFalse("The marker is still beside the database", marker.exists())
        assertArrayEquals("The fail-safe's marker was not moved intact", markerBytes, File(cleared.folder, marker.name).readBytes())
        assertEquals("The fail-safe's copies were changed", failSafeCopies, contents(copiesFolder).filterKeys { !it.startsWith(cleared.folder.name + File.separator) })
        assertEquals("The recheck changed the files", repaired, liveFiles())

        // Lifted, but not in this process, which may already hold the refused database.
        assertTrue(DatabaseDamage.wasLiftedInThisProcess(file))
        assertTrue(refusal.value)
        assertTrue(DatabaseDamage.isRefusedUntilRestart(file))
        assertThrows(DatabaseDamagedException::class.java) { session.openHelper.writableDatabase }
        assertEquals(repaired, liveFiles())

        // A new process opens it, with every entry.
        val second = inANewProcess()
        assertNotEquals(Process.myPid(), second.pid)
        assertNotEquals(first.pid, second.pid)
        assertNull("The new process could not open it: ${second.error}", second.error)
        assertEquals((1..200).map { entry(it).title }, second.titles)
    }

    /**
     * Damage Android itself finds while opening (a file that is not a
     * database): recorded by the fail-safe and signalled; the recheck keeps
     * the refusal and changes nothing, and a new process still refuses it.
     */
    @Test
    fun damageFoundWhileOpeningIsRecordedAndTheRecheckChangesNothing() = runBlocking {
        createWithEntries(50)
        overwrite(0, ByteArray(16) { 0x5A })

        val session = TrackoraDatabase.build(context, name)
        val refusal = DatabaseDamage.refusal(file)
        assertFalse(refusal.value)
        assertTrue("Opened a file that is not a database", runCatching { session.openHelper.writableDatabase }.isFailure)
        assertTrue(marker.exists())
        assertTrue(refusal.value)
        val markerBytes = marker.readBytes()

        val before = liveFiles()
        val outcome = DatabaseRecheck(file, work).run()
        assertEquals(outcome.toString(), Reason.NOT_A_DATABASE, (outcome as? Outcome.StillRefused)?.reason)
        assertEquals(before, liveFiles())
        assertArrayEquals(markerBytes, marker.readBytes())

        val fresh = inANewProcess()
        assertTrue(fresh.error.orEmpty(), fresh.error.orEmpty().contains("DatabaseDamagedException"))
        assertEquals(before, liveFiles())
    }
}
