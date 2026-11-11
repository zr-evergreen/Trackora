package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.evergreen.trackora.data.local.database.DatabaseRecheck.Outcome
import com.evergreen.trackora.data.local.database.DatabaseRecheck.Reason
import com.evergreen.trackora.data.local.database.DatabaseRecheck.Step
import com.evergreen.trackora.data.local.entity.WorkEntry
import com.evergreen.trackora.domain.model.Status
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Checking a refused database again ([DatabaseRecheck]) on real SQLite files.
 *
 * Every test starts from a database the fail-safe refused, with its marker
 * beside it, and checks two things besides the outcome: the database's own
 * files are byte for byte what they were, and the marker is either still
 * beside the database or, only after a passing check, moved intact into a new
 * folder of copies. Nothing a test did not create is ever removed.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRecheckTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "recheck-test"
    private val file: File get() = context.getDatabasePath(name)
    private val marker: File get() = File(file.path + DAMAGED_SUFFIX)
    private val copiesFolder: File get() = File(file.path + DAMAGED_COPIES_SUFFIX)
    private val work: File get() = File(context.noBackupFilesDir, "recheck-test-work")
    private val outside: File get() = File(context.noBackupFilesDir, "recheck-test-outside")
    private val markerText = "Recorded by the fail-safe in a test\n".toByteArray()
    private val stamp = 1_762_000_000_000L

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TrackoraDatabase::class.java)

    @Before
    @After
    fun deleteTestFiles() {
        context.deleteDatabase(name)
        file.parentFile?.listFiles { f -> f.name.startsWith(name) }?.forEach { it.deleteRecursively() }
        work.deleteRecursively()
        outside.deleteRecursively()
    }

    private fun recheck(
        freeSpace: (File) -> Long = File::getUsableSpace,
        reached: (Step) -> Unit = {},
    ) = DatabaseRecheck(file, work, { stamp }, freeSpace, reached)

    private class Crash(step: Step) : RuntimeException("Stopped at $step")

    // ---- Fixtures ------------------------------------------------------------

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

    private fun bytes(suffix: String): ByteArray? = File(file.path + suffix).takeIf { it.exists() }?.readBytes()

    /**
     * The files as a crash leaves them: [unsaved] more entries committed to the
     * `-wal` and never checkpointed into the database file.
     */
    private fun createCrashImage(saved: Int, unsaved: Int) = runBlocking {
        createWithEntries(saved)
        val database = TrackoraDatabase.build(context, name)
        val image = try {
            repeat(unsaved) { database.workEntryDao().insertEntry(entry(1_000 + it)) }
            listOf("", "-wal", "-shm").associateWith { bytes(it)!! }
        } finally {
            database.close()
        }
        context.deleteDatabase(name)
        image.forEach { (suffix, content) -> File(file.path + suffix).writeBytes(content) }
        assertTrue("The image has no unsaved changes in its -wal", image.getValue("-wal").isNotEmpty())
    }

    /** What the fail-safe leaves when it refuses the database: the marker beside it. */
    private fun refuse() = marker.writeBytes(markerText)

    /** The database's own files, by name: the database and everything SQLite keeps beside it. */
    private fun liveFiles(): Map<String, List<Byte>> =
        file.parentFile!!.listFiles { f -> f.isFile && (f.name == name || f.name.startsWith("$name-")) }!!
            .associate { it.name to it.readBytes().toList() }

    /** Every file under [folder], by its path relative to it, with its content. */
    private fun contents(folder: File): Map<String, List<Byte>> =
        folder.walkTopDown().filter { it.isFile }.associate { it.relativeTo(folder).path to it.readBytes().toList() }

    /** Writes [text] to a file named [fileName] in [folder], making the folder. */
    private fun put(folder: File, fileName: String, text: String) {
        folder.mkdirs()
        File(folder, fileName).writeText(text)
    }

    private fun overwrite(offset: Long, bytes: ByteArray) =
        RandomAccessFile(file, "rw").use { it.seek(offset); it.write(bytes) }

    private fun readOnly(query: String): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(query, null).use { it.moveToFirst(); it.getInt(0) }
        }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Every entry, opened by the app's own builder, in the order they were added. */
    private fun entries(): List<WorkEntry> = runBlocking {
        val database = TrackoraDatabase.build(context, name)
        try {
            database.workEntryDao().getAllEntries().first().sortedBy { it.id }
        } finally {
            database.close()
        }
    }

    private fun assertRefusedByTheApp() {
        val database = TrackoraDatabase.build(context, name)
        try {
            assertThrows(DatabaseDamagedException::class.java) { database.openHelper.writableDatabase }
        } finally {
            database.close()
        }
    }

    /** No copy of this check's own is left in its work folder. */
    private fun assertNoCopiesLeft() {
        val left = work.listFiles()?.map { it.name }.orEmpty()
        assertEquals("Copies left in the work folder", emptyList<String>(), left)
    }

    /** The outcome was a refusal or an unfinished check, and nothing was changed. */
    private fun assertKept(before: Map<String, List<Byte>>, outcome: Outcome) {
        assertTrue("Lifted: $outcome", outcome is Outcome.StillRefused || outcome is Outcome.NotChecked)
        assertTrue("The marker is no longer beside the database", marker.exists())
        assertArrayEquals(markerText, marker.readBytes())
        assertEquals("The database's files were changed", before, liveFiles())
        assertFalse("A folder of copies was made", copiesFolder.exists())
        assertNoCopiesLeft()
    }

    /** The refusal was lifted: the marker, intact, is in the outcome's folder with a note, and nothing else changed. */
    private fun assertCleared(before: Map<String, List<Byte>>, outcome: Outcome, schemaVersion: Int): File {
        assertTrue("Not lifted: $outcome", outcome is Outcome.Cleared)
        outcome as Outcome.Cleared
        assertEquals(schemaVersion, outcome.schemaVersion)
        assertFalse("The marker is still beside the database", marker.exists())
        assertArrayEquals(markerText, File(outcome.folder, marker.name).readBytes())
        assertEquals(copiesFolder.canonicalFile, outcome.folder.canonicalFile.parentFile)
        assertEquals(setOf(marker.name, DatabaseRecheck.NOTE), outcome.folder.list()!!.toSet())
        assertEquals("The database's files were changed", before, liveFiles())
        assertNoCopiesLeft()

        val note = File(outcome.folder, DatabaseRecheck.NOTE).readText()
        assertTrue(note, note.contains("PRAGMA integrity_check: ok"))
        assertTrue(note, note.contains("\n-wal: "))
        assertTrue(note, note.contains("Schema: version $schemaVersion, Room identity hash ${KnownSchemas.byVersion.getValue(schemaVersion).identityHash}"))
        assertTrue(note, note.contains("does not prove that every entry is"))
        before.forEach { (fileName, content) ->
            assertTrue("$fileName is not in the note:\n$note", note.contains("$fileName  ${content.size} bytes  sha256 ${sha256(content.toByteArray())}"))
        }
        return outcome.folder
    }

    // ---- Lifting the refusal -------------------------------------------------

    @Test
    fun withoutAMarkerNothingIsDone() {
        createWithEntries(2)
        val before = liveFiles()

        assertEquals(Outcome.NotRefused, recheck().run())
        assertEquals(before, liveFiles())
        assertFalse(work.exists())
        assertFalse(copiesFolder.exists())
    }

    @Test
    fun aSoundDatabaseWithUnsavedChangesInItsWalIsLiftedAndOpensWithEveryEntry() {
        createCrashImage(saved = 5, unsaved = 3)
        refuse()
        val before = liveFiles()
        assertTrue(before.keys.containsAll(listOf(name, "$name-wal", "$name-shm")))
        assertRefusedByTheApp()
        assertEquals("The refused app changed the files", before, liveFiles())

        assertCleared(before, recheck().run(), schemaVersion = 2)
        assertTrue("Not known as lifted in this process", DatabaseDamage.wasLiftedInThisProcess(file))

        val titles = entries().map { it.title }
        assertEquals((1..5).map { entry(it).title } + (0 until 3).map { entry(1_000 + it).title }, titles)
    }

    @Test
    fun aVersion1DatabaseIsLiftedAndThenUpgradedAsUsual() {
        helper.createDatabase(name, 1).use { db ->
            repeat(4) { i ->
                db.execSQL(
                    "INSERT INTO work_entries (title, description, quantity, status, date) VALUES (?, ?, ?, ?, ?)",
                    arrayOf("کار $i", null, i, "COMPLETED", "2026-01-0${i + 1}"),
                )
            }
        }
        refuse()
        val before = liveFiles()

        assertCleared(before, recheck().run(), schemaVersion = 1)

        val opened = entries()
        assertEquals((0 until 4).map { "کار $it" }, opened.map { it.title })
        assertEquals(2, readOnly("PRAGMA user_version"))
    }

    @Test
    fun aDatabaseUpgradedFromVersion1IsRecognisedAsVersion2() {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO work_entries (title, status, date) VALUES ('old', 'DELIVERED', '2025-03-20')")
        }
        helper.runMigrationsAndValidate(name, 2, true, *DatabaseMigrations.ALL).close()
        refuse()
        val before = liveFiles()

        assertCleared(before, recheck().run(), schemaVersion = 2)
        assertEquals(listOf("old"), entries().map { it.title })
    }

    /**
     * A transaction cut short in rollback-journal mode, as on a phone with
     * little memory: the database file holds pages it never committed, and
     * the `-journal` beside it the originals. SQLite rolls the copy back;
     * the live files stay exactly as they were, and the app later sees only
     * what was committed.
     */
    @Test
    fun aHotJournalIsRolledBackOnTheCopyAndLeftInPlace() {
        createWithEntries(20)
        val image: Map<String, ByteArray>
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.disableWriteAheadLogging()
            val mode = db.rawQuery("PRAGMA journal_mode", null).use { it.moveToFirst(); it.getString(0) }
            assertNotEquals("Still in WAL mode", "wal", mode.lowercase())
            val committed = bytes("")!!
            // A small cache, so SQLite writes uncommitted pages to the file
            // before the transaction ends.
            db.execSQL("PRAGMA cache_size = 10")
            db.beginTransaction()
            try {
                repeat(300) {
                    db.execSQL(
                        "INSERT INTO work_entries (title, status, date) VALUES (?, 'COMPLETED', '2026-02-01')",
                        arrayOf("uncommitted $it " + "x".repeat(3000)),
                    )
                }
                image = mapOf("" to bytes("")!!, "-journal" to bytes("-journal")!!)
            } finally {
                db.endTransaction() // Never marked successful: rolled back.
            }
            assertFalse("The file holds no uncommitted pages", committed.contentEquals(image.getValue("")))
        }
        // The files as a crash at that moment leaves them.
        image.forEach { (suffix, content) -> File(file.path + suffix).writeBytes(content) }
        assertTrue(image.getValue("-journal").isNotEmpty())
        refuse()
        val before = liveFiles()
        assertTrue(before.containsKey("$name-journal"))

        assertCleared(before, recheck().run(), schemaVersion = 2)
        assertEquals((1..20).map { entry(it).title }, entries().map { it.title })
    }

    @Test
    fun anExistingFolderIsNeverReusedAndEarlierCopiesAreLeftAsTheyAre() {
        createWithEntries(2)
        refuse()
        // A copy the fail-safe made, and a folder from an earlier recheck at the same moment.
        put(File(copiesFolder, "1761000000000"), name, "earlier copy")
        put(File(copiesFolder, "$stamp-cleared"), DatabaseRecheck.NOTE, "earlier note")
        put(File(copiesFolder, "$stamp-cleared-1"), marker.name, "earlier marker")
        val earlier = contents(copiesFolder)
        val before = liveFiles()

        val folder = assertCleared(before, recheck().run(), schemaVersion = 2)

        assertEquals("$stamp-cleared-2", folder.name)
        assertEquals(earlier, contents(copiesFolder).filterKeys { !it.startsWith("$stamp-cleared-2/") })
    }

    // ---- Keeping the refusal -------------------------------------------------

    @Test
    fun aDamagedPageKeepsTheRefusal() {
        createWithEntries(200)
        val root = readOnly("SELECT rootpage FROM sqlite_master WHERE name = 'work_entries'")
        val pageSize = readOnly("PRAGMA page_size")
        val pages = file.length() / pageSize
        listOf(root.toLong(), pages).forEach { page ->
            val clean = bytes("")!!
            overwrite((page - 1) * pageSize, ByteArray(pageSize) { 0xAB.toByte() })
            refuse()
            val before = liveFiles()

            val outcome = recheck().run()

            assertEquals("Page $page: $outcome", Reason.DAMAGED, (outcome as? Outcome.StillRefused)?.reason)
            assertKept(before, outcome)
            File(file.path).writeBytes(clean)
            marker.delete()
        }
    }

    @Test
    fun aFileWithoutSqlitesHeaderKeepsTheRefusal() {
        createWithEntries(3)
        overwrite(0, ByteArray(16) { 0x42 })
        refuse()
        val before = liveFiles()

        val outcome = recheck().run()

        assertEquals(Outcome.StillRefused(Reason.NOT_A_DATABASE, "The database file does not start with SQLite's header"), outcome)
        assertKept(before, outcome)
    }

    /**
     * The state the fail-safe refuses before anything opens: all that is
     * left of the entries is in a `-wal` beside an empty or missing file.
     * SQLite would accept the copy as an empty database, and delete the
     * `-wal`; the check refuses it and leaves the `-wal` alone.
     */
    @Test
    fun anEmptyOrMissingFileBesideAWalKeepsTheRefusalAndTheWal() {
        listOf("empty", "missing").forEach { case ->
            createCrashImage(saved = 2, unsaved = 2)
            if (case == "empty") file.writeBytes(ByteArray(0)) else assertTrue(file.delete())
            refuse()
            val before = liveFiles()

            val outcome = recheck().run()

            assertEquals(case, Reason.EMPTY_OR_MISSING, (outcome as? Outcome.StillRefused)?.reason)
            assertKept(before, outcome)
            assertTrue(case, (bytes("-wal")?.size ?: 0) > 0)
            deleteTestFiles()
        }
    }

    @Test
    fun aDatabaseThisAppDidNotMakeKeepsTheRefusal() {
        val variants: List<Pair<String, (SQLiteDatabase) -> Unit>> = listOf(
            "another app's database" to { db ->
                db.execSQL("DROP TABLE work_entries")
                db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT)")
            },
            "a changed identity hash" to { db ->
                db.execSQL("UPDATE room_master_table SET identity_hash = '00000000000000000000000000000000'")
            },
            "no identity hash" to { db -> db.execSQL("DROP TABLE room_master_table") },
            "a version this app never shipped" to { db -> db.version = 3 },
            "an extra column" to { db -> db.execSQL("ALTER TABLE work_entries ADD COLUMN extra TEXT") },
            "an index" to { db -> db.execSQL("CREATE INDEX by_date ON work_entries(date)") },
            "an extra table" to { db -> db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, body TEXT)") },
            "a view" to { db -> db.execSQL("CREATE VIEW recent AS SELECT * FROM work_entries") },
            "a trigger" to { db ->
                db.execSQL("CREATE TRIGGER wipe AFTER INSERT ON work_entries BEGIN DELETE FROM work_entries; END")
            },
        )
        variants.forEach { (case, change) ->
            createWithEntries(2)
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use(change)
            refuse()
            val before = liveFiles()

            val outcome = recheck().run()

            assertEquals("$case: $outcome", Reason.UNKNOWN_SCHEMA, (outcome as? Outcome.StillRefused)?.reason)
            assertKept(before, outcome)
            deleteTestFiles()
        }
    }

    @Test
    fun aFileBesideTheDatabaseThisCheckDoesNotHandleKeepsTheRefusal() {
        createWithEntries(2)
        // A super-journal, which names other files by their path.
        File(file.path + "-mj0123ABCD").writeText("super-journal")
        refuse()
        val before = liveFiles()

        val outcome = recheck().run()

        assertEquals(Reason.UNSUPPORTED_FILES, (outcome as? Outcome.NotChecked)?.reason)
        assertKept(before, outcome)
    }

    @Test
    fun tooLittleSpaceKeepsTheRefusalWithoutCopying() {
        createWithEntries(2)
        refuse()
        val before = liveFiles()
        var copied = false

        val outcome = recheck(freeSpace = { 0L }, reached = { copied = true }).run()

        assertEquals(Reason.NOT_ENOUGH_SPACE, (outcome as? Outcome.NotChecked)?.reason)
        assertFalse(copied)
        assertKept(before, outcome)
    }

    @Test
    fun aChangeAfterTheCopyWasTakenKeepsTheRefusal() {
        // Before the copy is checked, before the folder for the marker is
        // made, and just before the marker would move.
        listOf(Step.SNAPSHOT_TAKEN, Step.COPY_REMOVED, Step.NOTE_WRITTEN).forEach { at ->
            listOf("a changed -wal", "a new -journal").forEach { change ->
                createCrashImage(saved = 2, unsaved = 1)
                refuse()
                var before = liveFiles()

                val outcome = recheck(reached = { step ->
                    if (step != at) return@recheck
                    if (change == "a changed -wal") File(file.path + "-wal").appendBytes(byteArrayOf(1))
                    else File(file.path + "-journal").writeBytes(byteArrayOf(1))
                    before = liveFiles()
                }).run()

                assertEquals("$change at $at: $outcome", Reason.FILES_CHANGED, (outcome as? Outcome.NotChecked)?.reason)
                assertTrue(marker.exists())
                assertArrayEquals(markerText, marker.readBytes())
                assertEquals(before, liveFiles())
                assertNoCopiesLeft()
                // A folder made for the marker keeps its note, and is never moved into.
                copiesFolder.listFiles()?.forEach { assertFalse(File(it, marker.name).exists()) }
                deleteTestFiles()
            }
        }
    }

    /**
     * A damaged frame in the -wal: SQLite would read the frames before it and
     * silently drop every transaction after it, while integrity_check on the
     * rest still says "ok". The refusal stays.
     */
    @Test
    fun aWalWithADamagedFrameKeepsTheRefusal() {
        createCrashImage(saved = 5, unsaved = 6)
        val wal = File(file.path + "-wal")
        val frameSize = 24 + readOnly("PRAGMA page_size")
        assertTrue("The test needs at least 4 frames", wal.length() >= 32 + 4L * frameSize)
        // Frame 3's page: the frames after it, committed, would be lost.
        RandomAccessFile(wal, "rw").use { f ->
            val at = 32L + 2 * frameSize + 24 + 100
            f.seek(at); val b = f.read(); f.seek(at); f.write(b xor 0x5A)
        }
        refuse()
        val before = liveFiles()

        val outcome = recheck().run()

        assertEquals(outcome.toString(), Reason.UNVERIFIED_WAL, (outcome as? Outcome.StillRefused)?.reason)
        assertKept(before, outcome)
    }

    /**
     * The copy can look damaged because a file was changing while it was
     * copied. The files are compared before any finding is reported, so that
     * is reported as a change, and the user is asked to try again; only
     * unchanged files are reported as damaged. The refusal stays either way.
     */
    @Test
    fun aCopyThatLooksDamagedWhileTheFilesChangedIsReportedAsAChange() {
        fun tearTheCopy(fileName: String, offset: Long) {
            val copy = File(work.listFiles()!!.single(), fileName)
            RandomAccessFile(copy, "rw").use { f -> f.seek(offset); f.write(ByteArray(512) { 0xAB.toByte() }) }
        }
        // The entries' first page torn in the copy of a database with no -wal:
        // its header, so SQLite cannot miss it.
        createWithEntries(50)
        val root = readOnly("SELECT rootpage FROM sqlite_master WHERE name = 'work_entries'")
        val pageSize = readOnly("PRAGMA page_size")
        listOf(true, false).forEach { liveChanged ->
            refuse()
            var before = liveFiles()
            val outcome = recheck(reached = { step ->
                if (step != Step.SNAPSHOT_TAKEN) return@recheck
                tearTheCopy(name, (root - 1L) * pageSize)
                if (liveChanged) File(file.path + "-journal").writeBytes(ByteArray(0))
                before = liveFiles()
            }).run()
            val expected = if (liveChanged) Reason.FILES_CHANGED else Reason.DAMAGED
            assertEquals("live changed=$liveChanged: $outcome", expected, outcome.reasonOrNull())
            assertTrue(marker.exists())
            assertEquals(before, liveFiles())
            assertNoCopiesLeft()
            File(file.path + "-journal").delete()
        }

        // A -wal frame torn in the copy while the live -wal grew.
        deleteTestFiles()
        createCrashImage(saved = 3, unsaved = 4)
        refuse()
        var before = liveFiles()
        val outcome = recheck(reached = { step ->
            if (step != Step.SNAPSHOT_TAKEN) return@recheck
            tearTheCopy("$name-wal", 32L + 24 + 100)
            File(file.path + "-wal").appendBytes(byteArrayOf(1))
            before = liveFiles()
        }).run()
        assertEquals(outcome.toString(), Reason.FILES_CHANGED, outcome.reasonOrNull())
        assertTrue(marker.exists())
        assertEquals(before, liveFiles())
    }

    private fun Outcome.reasonOrNull(): Reason? = when (this) {
        is Outcome.StillRefused -> reason
        is Outcome.NotChecked -> reason
        else -> null
    }

    // ---- Interruptions -------------------------------------------------------

    /**
     * Stopping at each point, as a crash would: before the marker moves, the
     * database is still refused, and the app refuses it without opening it;
     * the next check finishes the job without reusing anything the stopped
     * one made. After the marker moved, the refusal is lifted.
     */
    @Test
    fun anInterruptionAtAnyPointLeavesAStateTheNextLaunchUnderstands() {
        Step.entries.forEach { at ->
            createCrashImage(saved = 3, unsaved = 2)
            refuse()
            val before = liveFiles()

            try {
                recheck(reached = { if (it == at) throw Crash(it) }).run()
                fail("Did not stop at $at")
            } catch (e: Crash) {
                // Expected.
            }

            assertEquals("At $at the database's files were changed", before, liveFiles())
            val made = if (copiesFolder.exists()) contents(copiesFolder) else emptyMap()
            val madeFolders = copiesFolder.list()?.toSet().orEmpty()
            if (at == Step.MARKER_MOVED) {
                assertFalse(at.name, marker.exists())
                assertEquals(Outcome.NotRefused, recheck().run())
            } else {
                assertTrue(at.name, marker.exists())
                assertRefusedByTheApp()
                assertEquals(at.name, before, liveFiles())
                val outcome = recheck().run()
                assertTrue("$at: $outcome", outcome is Outcome.Cleared)
                // What the stopped check made in the copies folder is left as it was.
                assertEquals(at.name, made, contents(copiesFolder).filterKeys { it in made })
                assertTrue(at.name, copiesFolder.list()!!.toSet().containsAll(madeFolders))
                assertFalse(at.name, (outcome as Outcome.Cleared).folder.name in madeFolders)
            }
            assertEquals(at.name, 1, copiesFolder.walkTopDown().count { it.isFile && it.name == marker.name })
            assertNoCopiesLeft()
            assertEquals(at.name, (1..3).map { entry(it).title } + (0 until 2).map { entry(1_000 + it).title }, entries().map { it.title })
            deleteTestFiles()
        }
    }

    /**
     * A check killed while it copied leaves its copy in the work folder; the
     * next check removes it, and only folders with the names it gives. A
     * look-alike that leads elsewhere is not followed.
     */
    @Test
    fun onlyCopiesThisCheckNamedAreRemoved() {
        createWithEntries(2)
        refuse()
        val leftover = File(work, "1761000000000-0a1b2c3d")
        put(leftover, name, "left behind")
        val kept = listOf("notes", "1761000000000-0A1B2C3D", "1761000000000-0a1b2c3d4", "-0a1b2c3d").map { folderName ->
            File(work, folderName).also { put(it, "x", folderName) }
        }
        val plainFile = File(work, "1761000000001-0a1b2c3d")
        plainFile.writeText("a file, not a folder")
        put(outside, "keep", "outside the work folder")
        val link = File(work, "1761000000002-0a1b2c3d")
        Os.symlink(outside.path, link.path)
        val before = liveFiles()

        val outcome = recheck().run()

        assertTrue("$outcome", outcome is Outcome.Cleared)
        assertEquals(before, liveFiles())
        assertFalse("The leftover copy is still there", leftover.exists())
        kept.forEach { assertEquals(it.name, File(it, "x").readText()) }
        assertEquals("a file, not a folder", plainFile.readText())
        assertEquals("outside the work folder", File(outside, "keep").readText())
        assertTrue(File(link, "keep").exists())
    }

    // ---- The app's own database ----------------------------------------------

    /**
     * Read from the marker alone, without opening anything; and once seen,
     * the refusal holds for the rest of the process, even after the marker
     * is moved. That leaves [DatabaseDamage.isRefusedUntilRestart] true for
     * the rest of this test process; no other test uses it.
     */
    @Test
    fun theAppsMarkerIsReadWithoutOpeningAnythingAndHoldsUntilRestart() {
        val appMarker = File(context.getDatabasePath(TrackoraDatabase.DATABASE_NAME).path + DAMAGED_SUFFIX)
        assertFalse("Test app already has a marker", appMarker.exists())
        try {
            assertFalse(DatabaseDamage.isRefused(context))
            assertFalse(DatabaseDamage.isRefusedUntilRestart(context))
            assertFalse(DatabaseDamage.refusal(context).value)
            assertEquals(null, DatabaseDamage.recordedAt(context))
            assertEquals(Outcome.NotRefused, DatabaseRecheck(context).run())

            appMarker.parentFile!!.mkdirs()
            appMarker.writeText("test")
            assertTrue(DatabaseDamage.isRefused(context))
            assertTrue(DatabaseDamage.isRefusedUntilRestart(context))
            assertTrue(DatabaseDamage.refusal(context).value)
            assertEquals(appMarker.lastModified(), DatabaseDamage.recordedAt(context))
            assertFalse(context.getDatabasePath(TrackoraDatabase.DATABASE_NAME).exists())

            assertTrue(appMarker.delete())
            assertFalse(DatabaseDamage.isRefused(context))
            assertTrue("Lifted before the process restarted", DatabaseDamage.isRefusedUntilRestart(context))
            assertTrue(DatabaseDamage.refusal(context).value)
        } finally {
            appMarker.delete()
        }
    }
}
