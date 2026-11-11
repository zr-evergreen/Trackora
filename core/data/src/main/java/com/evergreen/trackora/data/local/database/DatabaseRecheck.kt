package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * Checks a database the fail-safe refused, again, and lifts the refusal only
 * when a private copy of it proves sound.
 *
 * Nothing here opens the database itself. The database file and every SQLite
 * file beside it (`-wal`, `-shm`, `-journal`) are copied, while the refusal
 * keeps everything else away from them, into a folder of this check's own in
 * no_backup. The copy is opened instead, so SQLite can apply a `-wal` or roll
 * back a `-journal` as it would, on the copy only. The refusal is lifted when
 * all of these hold, and stays otherwise:
 *
 * - the database file is not empty and starts with SQLite's header;
 * - its `-wal`, if any, holds nothing SQLite would leave unread except
 *   proven leftovers from before a reset (see [WalCheck]): SQLite drops
 *   every transaction after a damaged frame without a word;
 * - `PRAGMA integrity_check` returns exactly one row, `ok`;
 * - `user_version`, Room's identity hash and the columns of `work_entries`
 *   are exactly those of a schema this app has shipped, and the database
 *   holds no table, view, trigger or index this app did not create (see
 *   [RecheckRules]);
 * - the files are unchanged, by size and SHA-256, since they were copied.
 *   They are compared before any result is given, so a change during the
 *   check is reported as one, never as damage.
 *
 * Lifting it moves the marker; nothing is deleted. A new folder inside the
 * copies folder ([DAMAGED_COPIES_SUFFIX]) gets a note of what was checked,
 * then the marker. Whether the marker still sits beside the database is the
 * whole state: if this is interrupted at any point, the database is still
 * refused unless the marker was moved, and it is moved only after every check
 * passed. The app must restart afterwards, because the open database
 * remembers its refusal.
 *
 * A passing check means SQLite found the file structurally sound and the
 * schema is one this app made. It does not prove that every entry is complete
 * or correct.
 *
 * The only things this deletes are its own copies: folders in its work folder
 * that it named, after the check that made them, or left behind by an earlier
 * check that was interrupted.
 */
class DatabaseRecheck internal constructor(
    private val database: File,
    private val workFolder: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val freeSpace: (File) -> Long = File::getUsableSpace,
    private val reached: (Step) -> Unit = {},
) {

    constructor(context: Context) : this(
        database = context.getDatabasePath(TrackoraDatabase.DATABASE_NAME),
        workFolder = File(context.noBackupFilesDir, WORK_FOLDER),
    )

    /** Points the check passes, in order; tests stop it at each. */
    internal enum class Step { SNAPSHOT_TAKEN, COPY_CHECKED, COPY_REMOVED, FOLDER_CREATED, NOTE_WRITTEN, MARKER_MOVED }

    sealed interface Outcome {
        /** There is no marker: the database is not refused, and nothing was done. */
        data object NotRefused : Outcome

        /** The copy proved sound and the marker was moved into [folder]. The app must restart. */
        data class Cleared(val folder: File, val schemaVersion: Int) : Outcome

        /** The database is still damaged, or is not one this app made. The marker stays. */
        data class StillRefused(val reason: Reason, val detail: String) : Outcome

        /** The check could not be completed. Nothing was changed, and the marker stays. */
        data class NotChecked(val reason: Reason, val detail: String) : Outcome
    }

    enum class Reason {
        EMPTY_OR_MISSING, NOT_A_DATABASE, DAMAGED, UNVERIFIED_WAL, UNKNOWN_SCHEMA,
        UNSUPPORTED_FILES, NOT_ENOUGH_SPACE, FILES_CHANGED, STORAGE_ERROR,
    }

    private val marker = File(database.path + DAMAGED_SUFFIX)

    /** Runs the check. It reads and copies files, so call it off the main thread. */
    fun run(): Outcome {
        if (!marker.exists()) return Outcome.NotRefused
        removeLeftovers()

        val files = sqliteFiles()
            ?: return Outcome.NotChecked(Reason.UNSUPPORTED_FILES, "There are files beside the database this check does not handle")
        if (!database.isFile || database.length() == 0L) {
            return Outcome.StillRefused(Reason.EMPTY_OR_MISSING, "The database file is empty or missing")
        }
        if (!hasSqliteHeader(database)) {
            return Outcome.StillRefused(Reason.NOT_A_DATABASE, "The database file does not start with SQLite's header")
        }

        workFolder.mkdirs()
        // Room for the copy, and for SQLite to grow it while applying a -wal.
        val needed = files.sumOf { it.length() } * 2 + SPACE_MARGIN
        val free = freeSpace(workFolder)
        if (free < needed) {
            return Outcome.NotChecked(Reason.NOT_ENOUGH_SPACE, "$free bytes free, $needed needed")
        }

        val copyFolder = newCopyFolder()
            ?: return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not create a folder for the copy")
        val snapshot: Map<String, Fingerprint>
        val finding: Finding
        try {
            snapshot = try {
                copy(files, copyFolder)
            } catch (e: IOException) {
                return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not copy the database: ${e.message}")
            } ?: return Outcome.NotChecked(Reason.FILES_CHANGED, "The database changed while it was being copied")
            reached(Step.SNAPSHOT_TAKEN)

            val copy = File(copyFolder, database.name)
            // Before SQLite opens the copy, which applies its -wal and removes it.
            val wal = try {
                WalCheck.check(File(copy.path + "-wal"), WalCheck.pageSizeOf(copy) ?: 0)
            } catch (e: IOException) {
                return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not read the copy's -wal: ${e.message}")
            }
            finding = if (wal is WalCheck.Result.Unverified) {
                Finding.Refuse(Reason.UNVERIFIED_WAL, wal.reason)
            } else {
                try {
                    when (val verdict = RecheckRules.decide(readFacts(copy))) {
                        is Verdict.Sound -> Finding.Lift(verdict.schema, wal)
                        is Verdict.Damaged -> Finding.Refuse(Reason.DAMAGED, verdict.problems.joinToString("; "))
                        is Verdict.UnknownSchema -> Finding.Refuse(Reason.UNKNOWN_SCHEMA, verdict.reason)
                    }
                } catch (e: SQLiteException) {
                    when (e) {
                        is SQLiteDiskIOException, is SQLiteFullException, is SQLiteCantOpenDatabaseException ->
                            return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not read the copy: ${e.message}")
                        else -> Finding.Refuse(Reason.DAMAGED, "SQLite could not read the copy: ${e.message}")
                    }
                }
            }
            reached(Step.COPY_CHECKED)
        } finally {
            removeOwned(copyFolder)
        }
        reached(Step.COPY_REMOVED)

        // What the copy showed holds only for files that are still as copied.
        // A file changed during the copy can make a sound database look
        // damaged, so this is checked before any finding is reported.
        if (!unchanged(snapshot)) {
            return Outcome.NotChecked(Reason.FILES_CHANGED, "The database changed while it was being checked")
        }
        return when (finding) {
            is Finding.Refuse -> Outcome.StillRefused(finding.reason, finding.detail)
            is Finding.Lift -> liftRefusal(snapshot, finding.schema, finding.wal)
        }
    }

    /** What the copy showed, before the files are compared again. */
    private sealed interface Finding {
        data class Lift(val schema: Schema, val wal: WalCheck.Result) : Finding
        data class Refuse(val reason: Reason, val detail: String) : Finding
    }

    /**
     * Moves the marker into a new folder of its own, after a note of what was
     * checked, and only if the files are still those that were checked: they
     * are compared once more, just before the marker moves.
     */
    private fun liftRefusal(snapshot: Map<String, Fingerprint>, schema: Schema, wal: WalCheck.Result): Outcome {
        val copies = File(database.path + DAMAGED_COPIES_SUFFIX)
        copies.mkdirs()
        val stamp = now()
        // mkdir creates the folder only if nothing has that name yet, so an
        // existing folder of copies is never reused or written into.
        val folder = (0 until MAX_NAME_ATTEMPTS)
            .map { n -> File(copies, "$stamp-cleared" + if (n == 0) "" else "-$n") }
            .firstOrNull { it.mkdir() }
            ?: return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not create a folder for the marker in ${copies.path}")
        syncFolder(copies)
        reached(Step.FOLDER_CREATED)

        try {
            val written = File(folder, "$NOTE.tmp")
            FileOutputStream(written).use { out ->
                out.write(note(stamp, snapshot, schema, wal).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!written.renameTo(File(folder, NOTE))) throw IOException("Could not name the note")
        } catch (e: IOException) {
            return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not write the note: ${e.message}")
        }
        syncFolder(folder)
        reached(Step.NOTE_WRITTEN)

        if (!unchanged(snapshot)) {
            return Outcome.NotChecked(Reason.FILES_CHANGED, "The database changed while it was being checked")
        }
        val moved = File(folder, marker.name)
        if (moved.exists() || !marker.renameTo(moved)) {
            return Outcome.NotChecked(Reason.STORAGE_ERROR, "Could not move the marker into ${folder.path}")
        }
        syncFolder(database.parentFile)
        syncFolder(folder)
        DatabaseDamage.lifted(database)
        reached(Step.MARKER_MOVED)
        Log.i(TAG, "${database.path} passed its recheck; the marker was moved to ${moved.path}")
        return Outcome.Cleared(folder, schema.version)
    }

    /** The database file and the SQLite files beside it, or null if any is a kind this does not handle. */
    private fun sqliteFiles(): List<File>? {
        val folder = database.parentFile ?: return emptyList()
        val beside = folder.listFiles { f -> f.name == database.name || f.name.startsWith(database.name + "-") }.orEmpty()
        val known = SIDECARS.map { database.name + it }.toSet()
        // Anything else, such as a super-journal (-mj…), points at files by
        // their path, so a copy could not be checked without touching them.
        if (beside.any { it.name !in known || !it.isFile }) return null
        return beside.sortedBy { it.name }
    }

    private fun hasSqliteHeader(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { input -> input.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private data class Fingerprint(val size: Long, val sha256: String)

    /**
     * Copies [files] into [folder], hashing each as it goes. Null if any file
     * changed while it was copied: its size or time differs before and after,
     * or fewer bytes came out than it held.
     */
    private fun copy(files: List<File>, folder: File): Map<String, Fingerprint>? =
        files.associate { source ->
            val sizeBefore = source.length()
            val timeBefore = source.lastModified()
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            source.inputStream().use { input ->
                FileOutputStream(File(folder, source.name)).use { output ->
                    val buffer = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        copied += n
                    }
                    output.fd.sync()
                }
            }
            if (copied != sizeBefore || source.length() != sizeBefore || source.lastModified() != timeBefore) return null
            source.name to Fingerprint(sizeBefore, digest.digest().toHex())
        }

    /** Whether the SQLite files beside the database are exactly those in [snapshot]. */
    private fun unchanged(snapshot: Map<String, Fingerprint>): Boolean {
        val now = sqliteFiles() ?: return false
        if (now.map { it.name }.toSet() != snapshot.keys) return false
        return now.all { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            snapshot[file.name] == Fingerprint(file.length(), digest.digest().toHex())
        }
    }

    private fun readFacts(copy: File): CopyFacts {
        val db = SQLiteDatabase.openDatabase(
            copy.path,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            KeepFiles,
        )
        try {
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
            val identityHash = try {
                db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } catch (e: SQLiteException) {
                null
            }
            val columns = db.rawQuery("PRAGMA table_info('work_entries')", null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(Column(c.getString(1), c.getString(2), c.getInt(3) != 0, c.getInt(5)))
                    }
                }
            }
            val indexCount = db.rawQuery("PRAGMA index_list('work_entries')", null).use { it.count }
            val foreignKeyCount = db.rawQuery("PRAGMA foreign_key_list('work_entries')", null).use { it.count }
            val otherObjects = db.rawQuery("SELECT type, name FROM sqlite_master", null).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val (type, name) = c.getString(0) to c.getString(1)
                        if (!RecheckRules.isExpectedObject(type, name)) add("$type $name")
                    }
                }
            }
            return CopyFacts(integrity, db.version, identityHash, columns, indexCount, foreignKeyCount, otherObjects)
        } finally {
            db.close()
        }
    }

    private fun newCopyFolder(): File? {
        repeat(MAX_NAME_ATTEMPTS) {
            val folder = File(workFolder, "${now()}-${Random.nextInt().toUInt().toString(16).padStart(8, '0')}")
            if (folder.mkdir()) return folder
        }
        return null
    }

    /** Removes copies an interrupted check left behind: only folders in the work folder with this check's own names. */
    private fun removeLeftovers() {
        workFolder.listFiles { f -> f.isDirectory && OWNED_NAME.matches(f.name) }?.forEach(::removeOwned)
    }

    private fun removeOwned(folder: File) {
        val owned = folder.canonicalFile.parentFile == workFolder.canonicalFile && OWNED_NAME.matches(folder.name)
        if (owned) folder.deleteRecursively() else Log.e(TAG, "Not removing ${folder.path}: not a copy this check made")
    }

    private fun note(stamp: Long, snapshot: Map<String, Fingerprint>, schema: Schema, wal: WalCheck.Result): String {
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(stamp))
        val files = snapshot.entries.joinToString("\n") { (name, f) -> "  $name  ${f.size} bytes  sha256 ${f.sha256}" }
        val walLine = when (wal) {
            is WalCheck.Result.Verified ->
                "-wal: ${wal.frames} frames; SQLite reads ${wal.validFrames}, up to the last commit at frame ${wal.committedFrames}; " +
                    "${wal.leftoverFrames} leftover frames from before a reset" + (if (wal.partialBytes > 0) ", and ${wal.partialBytes} bytes of one" else "")
            else -> "-wal: none"
        }
        return """
            |Trackora checked this database again at $time ($stamp ms) and found it sound.
            |
            |PRAGMA integrity_check: ok
            |$walLine
            |Schema: version ${schema.version}, Room identity hash ${schema.identityHash}, user_version ${schema.version}
            |Files checked, unchanged since they were copied for the check:
            |$files
            |
            |The marker, ${marker.name}, was then moved from the database folder
            |into this folder. If it is not here, it was not moved, and the
            |database is still refused.
            |
            |A passing check means SQLite found the file structurally sound and the
            |schema is one this app made. It does not prove that every entry is
            |complete or correct.
            |""".trimMargin()
    }

    private fun syncFolder(folder: File?) {
        folder ?: return
        try {
            val fd = Os.open(folder.path, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(fd)
            } finally {
                Os.close(fd)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync ${folder.path}", e)
        }
    }

    /** Never deletes anything: Android's default handler deletes a database it finds corrupt. */
    private object KeepFiles : DatabaseErrorHandler {
        override fun onCorruption(dbObj: SQLiteDatabase) {
            Log.w(TAG, "The copy being checked is corrupt: ${dbObj.path}")
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    internal companion object {
        const val WORK_FOLDER = "database-recheck"
        const val NOTE = "recovery-note.txt"
        private const val TAG = "TrackoraDatabase"
        private val SIDECARS = listOf("", "-wal", "-shm", "-journal")
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val OWNED_NAME = Regex("\\d+-[0-9a-f]{8}")
        private const val SPACE_MARGIN = 1L shl 20
        private const val BUFFER = 64 * 1024
        private const val MAX_NAME_ATTEMPTS = 100
    }
}
