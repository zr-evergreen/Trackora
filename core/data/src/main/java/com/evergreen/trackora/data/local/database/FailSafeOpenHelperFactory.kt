package com.evergreen.trackora.data.local.database

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.File

/**
 * Opens the database so that nothing beneath Room can delete it.
 *
 * androidx.sqlite 2.4.0, which Room opens databases with, deletes the
 * database in two situations:
 *
 * - **Corruption.** SQLite's error handler calls
 *   [SupportSQLiteOpenHelper.Callback.onCorruption], whose default deletes the
 *   database and its `-wal`, `-shm` and `-journal` files. When that happens
 *   while opening, Android opens the same path again straight away, finds
 *   nothing there and creates an empty database: the work log is replaced by
 *   an empty one without a word.
 * - **An [SQLiteException] from the open callback**, where Room checks the
 *   schema. After a second failed attempt the helper deletes the database and
 *   opens an empty one, whatever `allowDataLossOnRecovery` says. A passing
 *   disk I/O error was enough.
 *
 * This factory puts a [FailSafeCallback] around Room's callback and a
 * [FailSafeOpenHelper] around the helper:
 *
 * - An error while opening reaches the helper as a [DatabaseOpenException],
 *   not an SQLiteException, so the helper rethrows it instead of deleting.
 *   Nothing is recorded; the next attempt simply tries again.
 * - Corruption never deletes or empties the database. It is recorded in a
 *   marker file beside the database ([DAMAGED_SUFFIX]), the files there at that
 *   moment are copied aside, and every later call is refused with a
 *   [DatabaseDamagedException], in this launch and every later one. Nothing
 *   tries to repair it. Recovery is left to a person.
 * - A `-wal` holding changes beside an empty or missing database file is
 *   treated the same way, before anything opens it: SQLite would delete that
 *   `-wal` as it opened the database, and Room would then create an empty one.
 *
 * Its limits, most of them set by Android and androidx.sqlite beneath it:
 *
 * - **The `-wal` can be merged before it is copied.** When Android's own
 *   connection setup finds the corruption (a damaged header or schema, such as
 *   "file is not a database" from `PRAGMA journal_mode`), Android closes the
 *   failed connection before it reports the corruption. If the `-wal` holds
 *   changes, as it does after the app has been killed, that close checkpoints
 *   them into the database file and deletes the `-wal` and `-shm`. Committed
 *   entries survive, in the database file, but neither the files in place nor
 *   the copy are the originals any more. The files are copied as they were,
 *   `-wal` and `-shm` included, only when Room's schema check or a later query
 *   finds the damage.
 * - **It is opened once more in the same launch.** After an attempt to open
 *   fails, androidx.sqlite closes the database and tries again 500 ms later,
 *   within the same call. That second attempt runs Android's connection setup
 *   on the damaged file (recent Android versions may truncate a large `-wal`
 *   there) before [FailSafeCallback.onConfigure] refuses it, and Android then
 *   closes it again, which may checkpoint the `-wal`. Damage found once the
 *   database is open is followed by no further open or close.
 * - **The copies are best effort.** They are not atomic and are not retried at
 *   a later launch, so a kill or a full disk can leave them partial or missing.
 *   The marker's text, and the exception's message, say the files were kept as
 *   they were and copied either way. The marker is not synced to disk: if it
 *   is lost, or cannot be written, the next launch opens the database again
 *   and keeps using it until it reaches the damage again.
 * - **Not all corruption is reported.** Android does not call `onCorruption`
 *   for errors while preparing a statement or on BEGIN and COMMIT. Such an
 *   error fails that call without deleting anything, but is not recorded.
 * - **The refusal lasts until a person removes the marker,** even after a
 *   false report from failing storage. The user has no way out in the app, and
 *   clearing its storage or uninstalling it deletes the database, the marker
 *   and the copies. Auto Backup carries the marker and copies to a new device,
 *   which then refuses the database too.
 * - **An empty or missing database file without a `-wal`** holds nothing, and
 *   a new database is created there. SQLite also discards a hot `-journal`
 *   beside an empty file, which matters only in TRUNCATE journal mode (Room's
 *   choice on low-RAM devices).
 * - **Concurrent work.** A write on another thread that has already passed the
 *   refusal can commit while the copies are made, so they may not be a
 *   consistent set. Not closing a damaged database relies on it staying
 *   reachable, as the app's singleton does; Android's finalizer closes one
 *   that is garbage-collected, which checkpoints.
 *
 * It relies on the exact behaviour of androidx.sqlite 2.4.0 and Room 2.6.1:
 * androidx.sqlite deletes only for an SQLiteException that is the direct cause
 * of a failed `onOpen`, or for one outside the callbacks when
 * `allowDataLossOnRecovery` is on, and on corruption only through
 * [SupportSQLiteOpenHelper.Callback.onCorruption]; Room asks the helper for
 * the database before every use. Both exceptions here must stay non-SQLite
 * [IllegalStateException]s. The configuration is rebuilt field by field, and
 * the database's path worked out as androidx.sqlite does. androidx.sqlite
 * reaches the app only through Room and WorkManager, so a dependency update
 * can replace it: the control tests then show if it stops deleting, but not
 * if it starts deleting somewhere new. Recheck this class on any such update.
 *
 * Everything else is Room's own callback, unchanged.
 */
internal class FailSafeOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val name = configuration.name
        val file = when {
            name == null -> null
            configuration.useNoBackupDirectory -> File(configuration.context.noBackupFilesDir, name)
            else -> configuration.context.getDatabasePath(name)
        }
        val damage = Damage(file)
        val helper = delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(name)
                .callback(FailSafeCallback(configuration.callback, damage))
                .noBackupDirectory(configuration.useNoBackupDirectory)
                // Never recover from an error by deleting the database.
                .allowDataLossOnRecovery(false)
                .build()
        )
        return FailSafeOpenHelper(helper, damage)
    }
}

/** Appended to the database's path to name its corruption marker, and the folders its files are copied to. */
internal const val DAMAGED_SUFFIX = ".damaged"

/** The database was found corrupt. Its files are kept for recovery and it is not opened again. */
internal class DatabaseDamagedException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/** The database could not be opened this time, for a reason other than corruption. It was left as it is. */
internal class DatabaseOpenException(message: String, cause: Throwable) :
    IllegalStateException(message, cause)

/**
 * Whether the database at [file] has been found corrupt, remembered in a
 * marker file beside it so that the refusal outlives this launch.
 */
private class Damage(private val file: File?) {

    private val marker = file?.let { File(it.path + DAMAGED_SUFFIX) }

    @Volatile
    var isFound: Boolean = marker?.exists() == true
        private set

    fun refuseIfFound(cause: Throwable? = null) {
        if (isFound) {
            throw DatabaseDamagedException(
                "The database ${file?.path} was found corrupt and is not opened. " +
                    "Its files are kept as they were, and copied beside it, for recovery.",
                cause
            )
        }
    }

    /**
     * A `-wal` with changes in it beside an empty or missing database file
     * holds all that is left of those changes, and SQLite deletes such a
     * `-wal` when it opens the database. SQLite never leaves a database in
     * that state itself, so it is recorded as damage before anything opens it.
     */
    fun recordWalWithoutDatabase() {
        if (isFound || file == null) return
        if (file.length() == 0L && File(file.path + "-wal").length() > 0L) {
            record(file.path, "Its -wal holds changes, but the database file is empty or missing")
        }
    }

    /**
     * Records that the database at [path] is damaged: the marker first, so
     * that the refusal survives even if the copies do not fit on a full disk,
     * then a copy of each of its files as they are now, which may already have
     * had their `-wal` merged (see [FailSafeOpenHelperFactory]). Nothing is
     * deleted, moved or overwritten here, and a failure here changes nothing
     * else. The copies are not atomic and are not retried.
     */
    @Synchronized
    fun record(path: String?, reason: String = "SQLite reported it as corrupt") {
        val first = !isFound
        isFound = true
        if (!first || file == null || marker == null) return

        val path = path ?: file.path
        val folder = generateSequence(0) { it + 1 }
            .map { n -> File(file.parentFile, file.name + DAMAGED_SUFFIX + "-" + System.currentTimeMillis() + (if (n == 0) "" else "-$n")) }
            .first { !it.exists() }

        try {
            marker.writeText(
                "$reason, at ${System.currentTimeMillis()} ms.\n" +
                    "The app no longer opens it. Its files were left as they were and copied to ${folder.path}.\n" +
                    "Remove this file only once the database has been recovered or replaced.\n"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not write the corruption marker for $path", e)
        }

        try {
            folder.mkdirs()
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                val source = File(path + suffix)
                if (!source.exists()) continue
                try {
                    source.copyTo(File(folder, source.name), overwrite = false)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not copy ${source.path} aside", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not copy the corrupt database $path aside", e)
        }
        Log.e(TAG, "$reason: $path. It is kept and no longer opened")
    }

    private companion object {
        const val TAG = "TrackoraDatabase"
    }
}

/** Room's callback, except where androidx.sqlite would answer by deleting the database. */
private class FailSafeCallback(
    private val delegate: SupportSQLiteOpenHelper.Callback,
    private val damage: Damage,
) : SupportSQLiteOpenHelper.Callback(delegate.version) {

    override fun onConfigure(db: SupportSQLiteDatabase) {
        // Corruption reported while this very open was under way: Android
        // opens the file again after reporting it, and if that succeeds the
        // database must still not be used. This also refuses androidx.sqlite's
        // retry, but only after Android's connection setup has run on the file.
        damage.refuseIfFound()
        delegate.onConfigure(db)
    }

    override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        delegate.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
        delegate.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) {
        try {
            delegate.onOpen(db)
        } catch (e: SQLiteException) {
            // Anything but an SQLiteException: from the open callback, the
            // helper answers an SQLiteException by deleting the database.
            // Corruption has already been recorded by onCorruption.
            damage.refuseIfFound(e)
            throw DatabaseOpenException("The database could not be opened; it was kept as it is.", e)
        }
    }

    override fun onCorruption(db: SupportSQLiteDatabase) {
        // Not super: its default deletes the database and its files.
        damage.record(db.path)
    }
}

/**
 * The helper, refusing every call once the database has been found corrupt,
 * and not closing it: closing ends with a checkpoint that writes the `-wal`
 * into the database file. androidx.sqlite's own retry, inside the call that
 * found the damage, is beyond its reach (see [FailSafeOpenHelperFactory]).
 */
private class FailSafeOpenHelper(
    private val delegate: SupportSQLiteOpenHelper,
    private val damage: Damage,
) : SupportSQLiteOpenHelper by delegate {

    override val writableDatabase: SupportSQLiteDatabase
        get() = open { delegate.writableDatabase }

    override val readableDatabase: SupportSQLiteDatabase
        get() = open { delegate.readableDatabase }

    /** Whether the database is open, after which its files need no checking before each use. */
    @Volatile
    private var isOpen = false

    private inline fun open(get: () -> SupportSQLiteDatabase): SupportSQLiteDatabase {
        if (!isOpen) damage.recordWalWithoutDatabase()
        damage.refuseIfFound()
        return try {
            get().also { isOpen = true }
        } catch (e: SQLiteException) {
            // Corruption reported while opening surfaces as SQLite's own
            // exception; say what was done about it.
            damage.refuseIfFound(e)
            throw e
        }
    }

    override fun close() {
        if (!damage.isFound) {
            isOpen = false
            delegate.close()
        }
    }
}
