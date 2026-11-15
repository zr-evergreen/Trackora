package com.evergreen.trackora.export

import com.evergreen.trackora.backup.BackupApp
import com.evergreen.trackora.backup.BackupCheck
import com.evergreen.trackora.backup.BackupEntry
import com.evergreen.trackora.backup.BackupException
import com.evergreen.trackora.backup.BackupFormat
import com.evergreen.trackora.backup.BackupManifest
import com.evergreen.trackora.backup.BackupPhoto
import com.evergreen.trackora.backup.BackupProblem
import com.evergreen.trackora.backup.BackupReader
import com.evergreen.trackora.backup.BackupSettings
import com.evergreen.trackora.backup.BackupStatus
import com.evergreen.trackora.backup.BackupWriter
import com.evergreen.trackora.backup.MissingPhoto
import com.evergreen.trackora.backup.PhotoLinks
import com.evergreen.trackora.data.local.database.DatabaseRefusedException
import com.evergreen.trackora.data.local.database.StoredEntry
import com.evergreen.trackora.data.local.database.UnreadableEntryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SyncFailedException
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/** What building a backup produced. */
sealed interface BackupBuild {
    /**
     * A complete backup, read back with [BackupReader] and found identical to
     * what was written, waiting in private storage to be saved or discarded.
     */
    class Ready internal constructor(
        val fileName: String,
        val summary: BackupSummary,
        internal val file: File,
        internal val ticket: BackupExport.Ticket,
    ) : BackupBuild

    /** The database is refused while it awaits recovery, so it wasn't read. */
    data object Refused : BackupBuild

    data class Failed(val reason: BackupFailure, val detail: String) : BackupBuild
}

data class BackupSummary(val entries: Long, val photos: Int, val missingPhotos: Long)

/** Why a backup couldn't be made or saved. Each has its own message. */
enum class BackupFailure {
    /** An entry or setting can't be stored exactly as it is (a value of the wrong type, a status the app can't read). */
    DATA,

    /** A photo changed, or stopped being readable, while it was being copied. */
    PHOTO_CHANGED,

    /** More files or bytes than a backup may hold. */
    TOO_LARGE,

    /** The backup couldn't be written in private storage: no space, or a write failed. */
    STORAGE,

    /** The finished backup, read back, wasn't exactly what was written. */
    VERIFICATION,

    /** The backup couldn't be written to the chosen place. */
    SAVE,

    /** The copy written to the chosen place, read back, isn't the verified backup. */
    SAVE_VERIFICATION,
}

/** How far building a backup has got. */
sealed interface BackupProgress {
    data object Settings : BackupProgress
    data class Entries(val read: Long) : BackupProgress
    data class Photos(val done: Int, val total: Int) : BackupProgress
    data object Verifying : BackupProgress
}

sealed interface BackupSave {
    data class Saved(val summary: BackupSummary) : BackupSave
    data class Failed(val reason: BackupFailure, val detail: String) : BackupSave
}

/**
 * Makes a backup of every entry, every photo an entry uses and the settings,
 * in the format of `:core:backup`, without changing anything it reads.
 *
 * It works in two steps. [build] writes the backup into a folder of its own
 * under [workFolder], then reads it back with the real [BackupReader] and
 * compares everything read with everything written. Only a backup that
 * passes is offered, as [BackupBuild.Ready], to [save], which copies it to
 * the place the user chose and reads that copy back to check it. Whatever
 * happens, [discard] (or a failed [build]) deletes only that folder.
 *
 * Its sources are passed in: [readEntries] (StoredEntries in the app),
 * [readSettings], the [photoFolder] that app photo links point into, and
 * [isRefused], checked first so a refused database is never read.
 */
class BackupExport(
    private val workFolder: File,
    private val photoFolder: () -> File?,
    private val isRefused: () -> Boolean,
    private val readSettings: suspend () -> BackupSettings,
    private val readEntries: (onRow: (StoredEntry) -> Unit) -> Long?,
    private val app: BackupApp,
    private val databaseVersion: Int,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** Called with the finished file before it is read back; tests use it to damage the file. */
    internal var beforeVerify: (File) -> Unit = {}

    /**
     * Names one [build], so its folder can be deleted with [discard] even if
     * the build is cancelled just as it finishes, before its result is seen.
     */
    class Ticket internal constructor()

    fun ticket(): Ticket = Ticket()

    /** One backup is built at a time, and the folders of backups awaiting [save] are kept. */
    private val building = Mutex()
    private val pending: MutableMap<Ticket, File> = Collections.synchronizedMap(HashMap())

    suspend fun build(ticket: Ticket = Ticket(), onProgress: (BackupProgress) -> Unit = {}): BackupBuild = building.withLock {
        withContext(Dispatchers.IO) {
            if (isRefused()) return@withContext BackupBuild.Refused
            removeLeftovers()
            val folder = newFolder() ?: return@withContext BackupBuild.Failed(BackupFailure.STORAGE, "The work folder couldn't be created")
            var stage = Stage.SETTINGS
            var ready: BackupBuild.Ready? = null
            try {
                val created = now()
                val fileName = fileName(created)
                val file = File(folder, fileName)

                onProgress(BackupProgress.Settings)
                val settings = readSettings()
                val photos = PhotoPlan(photoFolder())
                val digest = EntryDigest()
                val manifest: BackupManifest = FileOutputStream(file).use { fileOut ->
                    val out = StorageOutput(fileOut).buffered(BUFFER)
                    BackupWriter(out, created, app, databaseVersion).use { writer ->
                        writer.writeSettings(settings)

                        stage = Stage.ENTRIES
                        var read = 0L
                        val context = coroutineContext
                        val mark = readEntries { row ->
                            context.ensureActive()
                            val entry = entryOf(row, photos)
                            writer.writeEntry(entry)
                            digest.add(entry)
                            read++
                            if (read % PROGRESS_STEP == 0L) onProgress(BackupProgress.Entries(read))
                        }
                        onProgress(BackupProgress.Entries(read))

                        stage = Stage.PHOTOS
                        val included = photos.included()
                        included.forEachIndexed { i, photo ->
                            coroutineContext.ensureActive()
                            onProgress(BackupProgress.Photos(i, included.size))
                            writer.writePhoto(photo.name) { PhotoInput(photo.file) }
                        }
                        onProgress(BackupProgress.Photos(included.size, included.size))

                        stage = Stage.FINISHING
                        writer.finish(mark).also {
                            out.flush()
                            storage { fileOut.fd.sync() }
                        }
                    }
                }

                stage = Stage.VERIFYING
                beforeVerify(file)
                onProgress(BackupProgress.Verifying)
                verify(file, manifest, settings, digest.value())?.let { return@withContext it }
                // Cancelled by now, it is deleted below rather than handed over.
                coroutineContext.ensureActive()

                ready = BackupBuild.Ready(
                    fileName = fileName,
                    summary = BackupSummary(manifest.entryCount, manifest.photoCount, manifest.missingPhotoCount),
                    file = file,
                    ticket = ticket,
                )
                pending[ticket] = folder
                ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: DatabaseRefusedException) {
                BackupBuild.Refused
            } catch (e: StorageException) {
                BackupBuild.Failed(BackupFailure.STORAGE, e.message.orEmpty())
            } catch (e: PhotoReadException) {
                BackupBuild.Failed(BackupFailure.PHOTO_CHANGED, e.message.orEmpty())
            } catch (e: BackupException) {
                BackupBuild.Failed(failureOf(e.problem, stage), e.message.orEmpty())
            } catch (e: IOException) {
                readFailure(stage, e)
            } catch (e: RuntimeException) {
                // SQLite and Room report errors unchecked; anything else unexpected is not a success either.
                readFailure(stage, e)
            } finally {
                if (ready == null) delete(folder)
            }
        }
    }

    /**
     * Copies [ready] to [open]'s stream, then reads the copy back through
     * [reopen] and checks it is byte for byte the verified backup. The place
     * it was written to was chosen by the user; it is never deleted here.
     */
    suspend fun save(ready: BackupBuild.Ready, open: () -> OutputStream?, reopen: () -> InputStream?): BackupSave =
        withContext(Dispatchers.IO) {
            try {
                val expected = Fingerprint.of(ready.file.inputStream())
                val out = open() ?: return@withContext BackupSave.Failed(BackupFailure.SAVE, "The chosen place can't be written to")
                out.use {
                    ready.file.inputStream().use { input -> input.copyTo(out, BUFFER) }
                    out.flush()
                    if (out is FileOutputStream) {
                        try {
                            out.fd.sync()
                        } catch (e: SyncFailedException) {
                            // A pipe to a cloud provider can't be synced; reading it back below is the check.
                        }
                    }
                }
                val input = reopen() ?: return@withContext BackupSave.Failed(BackupFailure.SAVE_VERIFICATION, "The saved copy can't be read back")
                val saved = Fingerprint.of(input)
                if (saved != expected) {
                    return@withContext BackupSave.Failed(
                        BackupFailure.SAVE_VERIFICATION,
                        "The saved copy is ${saved.size} bytes and doesn't match the verified backup of ${expected.size} bytes",
                    )
                }
                BackupSave.Saved(ready.summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                BackupSave.Failed(BackupFailure.SAVE, e.message ?: e.javaClass.simpleName)
            } catch (e: RuntimeException) {
                // A document provider may refuse with any unchecked exception (security, a bad mode, a crash).
                BackupSave.Failed(BackupFailure.SAVE, e.message ?: e.javaClass.simpleName)
            }
        }

    /** Deletes [ready]'s folder: it was created by its [build] and holds only the backup. */
    fun discard(ready: BackupBuild.Ready) = discard(ready.ticket)

    /** Deletes the folder of the build [ticket] named, if it finished and is still kept. */
    fun discard(ticket: Ticket) {
        pending.remove(ticket)?.let(::delete)
    }

    private enum class Stage { SETTINGS, ENTRIES, PHOTOS, FINISHING, VERIFYING }

    /** Reads the backup back and compares it with what was written; the failure, or null if it matches. */
    private fun verify(file: File, written: BackupManifest, settings: BackupSettings, entries: String): BackupBuild.Failed? {
        val digest = EntryDigest()
        val result = BackupReader.readEntries(file) { digest.add(it) }
        if (result !is BackupCheck.Valid) {
            val invalid = result as BackupCheck.Invalid
            return BackupBuild.Failed(BackupFailure.VERIFICATION, "The reader refused the backup: ${invalid.problem}: ${invalid.detail}")
        }
        val backup = result.backup
        return when {
            backup.manifest != written -> BackupBuild.Failed(BackupFailure.VERIFICATION, "The manifest read back differs from the one written")
            backup.settings != settings -> BackupBuild.Failed(BackupFailure.VERIFICATION, "The settings read back differ from those written")
            digest.value() != entries -> BackupBuild.Failed(BackupFailure.VERIFICATION, "The entries read back differ from those written")
            else -> null
        }
    }

    /**
     * An error while reading entries or settings. If the fail-safe refused
     * the database meanwhile (it found damage), that is what is reported.
     */
    private fun readFailure(stage: Stage, e: Exception): BackupBuild = when {
        isRefused() -> BackupBuild.Refused
        stage == Stage.SETTINGS || stage == Stage.ENTRIES -> BackupBuild.Failed(BackupFailure.DATA, e.message ?: e.javaClass.simpleName)
        else -> BackupBuild.Failed(BackupFailure.STORAGE, e.message ?: e.javaClass.simpleName)
    }

    private fun failureOf(problem: BackupProblem, stage: Stage): BackupFailure = when {
        problem == BackupProblem.TOO_LARGE -> BackupFailure.TOO_LARGE
        stage == Stage.PHOTOS -> BackupFailure.PHOTO_CHANGED
        stage == Stage.VERIFYING -> BackupFailure.VERIFICATION
        else -> BackupFailure.DATA
    }

    private fun entryOf(row: StoredEntry, photos: PhotoPlan): BackupEntry {
        val status = BackupStatus.entries.firstOrNull { it.name == row.status }
            ?: throw UnreadableEntryException("Entry ${row.id} has a status, \"${row.status.take(40)}\", the app can't read")
        return BackupEntry(
            id = row.id,
            title = row.title,
            description = row.description,
            quantity = row.quantity,
            status = status,
            date = row.date,
            customField1 = row.customField1,
            customField2 = row.customField2,
            customField3 = row.customField3,
            photo = row.photoUri?.let(photos::photoFor),
        )
    }

    private fun fileName(instant: Instant): String =
        "Trackora-backup-" + FILE_TIME.format(instant.atZone(zone())) + ".zip"

    /** A new folder for one export, named so it can't collide with another. */
    private fun newFolder(): File? {
        if (!workFolder.isDirectory && !workFolder.mkdirs()) return null
        repeat(5) {
            val folder = File(workFolder, "${System.currentTimeMillis()}-${"%08x".format(Locale.ROOT, Random.nextInt())}")
            if (folder.mkdir()) return folder
        }
        return null
    }

    /**
     * Deletes the folders of earlier exports that never finished (the app
     * was closed during one). Only folders this class names, inside
     * [workFolder], and none awaiting [save].
     */
    private fun removeLeftovers() {
        workFolder.listFiles()?.forEach { folder ->
            if (folder.isDirectory && OPERATION.matches(folder.name) && folder !in pending.values) delete(folder)
        }
    }

    private fun delete(folder: File) {
        if (folder.parentFile?.canonicalFile == workFolder.canonicalFile && OPERATION.matches(folder.name)) {
            folder.deleteRecursively()
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val PROGRESS_STEP = 200L
        val OPERATION = Regex("[0-9]+-[0-9a-f]{8}")
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm", Locale.ROOT)
    }
}

/**
 * Decides, once per photo file, whether it goes into the backup and under
 * which name, so a photo several entries share is included once.
 */
internal class PhotoPlan(private val folder: File?) {

    class Included(val name: String, val file: File)

    private sealed interface Decision {
        class In(val name: String) : Decision
        class Out(val reason: MissingPhoto) : Decision
    }

    private val decisions = HashMap<String, Decision>()
    private val takenNames = HashSet<String>()
    private val included = ArrayList<Included>()

    /** The photo of an entry whose stored link is [uri], kept as it is. */
    fun photoFor(uri: String): BackupPhoto {
        val link = PhotoLinks.parse(uri) as? PhotoLinks.Link.AppFile
            ?: return BackupPhoto.missing(uri, MissingPhoto.NOT_APP_FILE)
        return when (val decision = decisions.getOrPut(link.path) { decide(link) }) {
            is Decision.In -> BackupPhoto.included(decision.name, uri)
            is Decision.Out -> BackupPhoto.missing(uri, decision.reason)
        }
    }

    /** The photos to include, in the ascending name order the writer takes them in. */
    fun included(): List<Included> = included.sortedBy { it.name }

    private fun decide(link: PhotoLinks.Link.AppFile): Decision {
        val root = folder ?: return Decision.Out(MissingPhoto.UNREADABLE)
        val file = File(root, link.path)
        // Each part of the path is a plain name, but a symbolic link could still lead out of the folder.
        val inside = try {
            file.canonicalPath.startsWith(root.canonicalPath + File.separator)
        } catch (e: IOException) {
            false
        }
        if (!inside) return Decision.Out(MissingPhoto.NOT_APP_FILE)
        if (!file.exists()) return Decision.Out(MissingPhoto.NOT_FOUND)
        if (!file.isFile) return Decision.Out(MissingPhoto.UNREADABLE)
        if (file.length() > BackupFormat.MAX_PHOTO_BYTES) return Decision.Out(MissingPhoto.TOO_LARGE)
        val readable = try {
            FileInputStream(file).use { it.read() }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        }
        if (!readable) return Decision.Out(MissingPhoto.UNREADABLE)

        // Photos in different folders can share a file name; a backup's photos sit in one.
        val wanted = link.fileName
        val name = if (wanted.lowercase() !in takenNames) wanted else PhotoLinks.uniqueName(wanted, takenNames)
        takenNames += name.lowercase()
        included += Included(name, file)
        return Decision.In(name)
    }
}

/** Writing the backup in private storage failed: no space, or the write itself. */
internal class StorageException(message: String, cause: Throwable?) : IOException(message, cause)

private inline fun <T> storage(block: () -> T): T = try {
    block()
} catch (e: IOException) {
    throw StorageException("The backup couldn't be written: ${e.message}", e)
}

/** The backup file's stream, whose errors are told apart from errors reading entries or photos. */
private class StorageOutput(out: OutputStream) : java.io.FilterOutputStream(out) {
    override fun write(b: Int) = storage { out.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) = storage { out.write(b, off, len) }
    override fun flush() = storage { out.flush() }
    override fun close() = storage { out.close() }
}

/** A photo failed to read: it changed, or stopped being readable, after it was planned. */
internal class PhotoReadException(message: String, cause: Throwable?) : IOException(message, cause)

/** A photo file whose read errors are told apart from errors writing the backup. */
private class PhotoInput(file: File) : FilterInputStream(open(file)) {
    override fun read(): Int = guard { super.read() }
    override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw PhotoReadException("A photo couldn't be read: ${e.message}", e)
    }

    private companion object {
        fun open(file: File): InputStream = try {
            FileInputStream(file)
        } catch (e: IOException) {
            throw PhotoReadException("${file.name} is no longer there or can't be read", e)
        }
    }
}

/**
 * A digest of entries, field by field with their lengths, so the entries read
 * back can be compared with those written without keeping either in memory.
 */
internal class EntryDigest {
    private val sha = MessageDigest.getInstance("SHA-256")
    private val out = DataOutputStream(DigestOutputStream(Discard, sha))

    fun add(entry: BackupEntry) {
        out.writeLong(entry.id)
        text(entry.title)
        text(entry.description)
        number(entry.quantity)
        text(entry.status.name)
        text(entry.date)
        text(entry.customField1)
        text(entry.customField2)
        text(entry.customField3)
        val photo = entry.photo
        if (photo == null) {
            out.writeByte(0)
        } else {
            out.writeByte(1)
            text(photo.file)
            text(photo.originalUri)
            text(photo.missing?.name)
        }
    }

    fun value(): String {
        out.flush()
        return sha.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun text(value: String?) {
        if (value == null) {
            out.writeByte(0)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeByte(1)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun number(value: Long?) {
        if (value == null) {
            out.writeByte(0)
        } else {
            out.writeByte(1)
            out.writeLong(value)
        }
    }

    private object Discard : OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}

/** A stream's size and SHA-256. */
internal data class Fingerprint(val size: Long, val sha256: String) {
    companion object {
        fun of(input: InputStream): Fingerprint = input.use {
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var size = 0L
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                sha.update(buffer, 0, n)
                size += n
            }
            Fingerprint(size, sha.digest().joinToString("") { b -> "%02x".format(Locale.ROOT, b) })
        }
    }
}
