package com.evergreen.trackora.backup

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a backup in [BackupFormat] to [output], in one pass, so [output] can
 * be any stream, such as one from the system file picker.
 *
 * The JSON files are deflated and the photos stored as they are, always at
 * one compression level: on Android 12 and 13, changing ZipOutputStream's
 * level between files writes corrupt data that no reader, Android's own
 * included, can unpack.
 *
 * Call in this order: [writeSettings] once, [writeEntry] for every entry in
 * ascending ID order, [writePhoto] for every photo an included entry names,
 * in ascending name order, then [finish]. Then [close].
 *
 * The writer checks everything with the same rules and limits as
 * [BackupReader], so it never produces a file the reader refuses: anything
 * that would break them throws [BackupException] instead, and the writer is
 * then unusable. A file whose writer didn't reach [finish] has no manifest,
 * so every reader refuses it; the caller deletes it.
 */
class BackupWriter internal constructor(
    output: OutputStream,
    createdAt: Instant,
    private val app: BackupApp,
    private val databaseVersion: Int,
    private val limits: Limits,
) : Closeable {

    constructor(output: OutputStream, createdAt: Instant, app: BackupApp, databaseVersion: Int) :
        this(output, createdAt, app, databaseVersion, Limits.V1)

    private enum class Stage { SETTINGS, ENTRIES, PHOTOS, DONE, FAILED }

    private val createdAt = createdAt.toString()
    private val zip = ZipOutputStream(output)
    private var stage = Stage.SETTINGS
    private val files = ArrayList<BackupFile>()
    private var totalBytes = 0L

    private val entriesDigest: MessageDigest = sha256()
    private var entriesBytes = 0L
    private var entryCount = 0L
    private var missingPhotos = 0L
    private var lastId: Long? = null

    /** A photo the entries include, and whether it has been written yet. */
    private class PhotoUse(val name: String) {
        var written = false
    }

    /** Photos the entries include, ignoring case, so names differing only in case are caught. */
    private val usedPhotos = java.util.TreeMap<String, PhotoUse>(String.CASE_INSENSITIVE_ORDER)
    private var writtenPhotos = 0
    private var lastPhoto: String? = null

    init {
        BackupRules.appProblem(app)?.let { throw BackupException(BackupProblem.INVALID_MANIFEST, "The app fields can't go in a backup: $it") }
    }

    fun writeSettings(settings: BackupSettings) = step(Stage.SETTINGS) {
        BackupRules.settingsProblem(settings)?.let { fail(BackupProblem.INVALID_SETTINGS, it) }
        val bytes = CanonicalJson.encode(settings).toByteArray(Charsets.UTF_8)
        if (bytes.size > limits.settingsBytes) fail(BackupProblem.TOO_LARGE, "The settings are larger than ${limits.settingsBytes} bytes")
        writeWhole(BackupFormat.SETTINGS, bytes)
        zip.putNextEntry(zipEntry(BackupFormat.ENTRIES))
        stage = Stage.ENTRIES
    }

    fun writeEntry(entry: BackupEntry) = step(Stage.ENTRIES) {
        BackupRules.entryProblem(entry)?.let { fail(BackupProblem.INVALID_ENTRY, "Entry ${entry.id}: $it") }
        lastId?.let { if (entry.id <= it) fail(BackupProblem.INVALID_ENTRY, "Entry ${entry.id} comes after $it: IDs must ascend, each once") }
        entry.photo?.file?.let { name ->
            val known = usedPhotos[name]
            if (known == null) usedPhotos[name] = PhotoUse(name)
            else if (known.name != name) fail(BackupProblem.DUPLICATE_FILE, "Photos \"${known.name}\" and \"$name\" differ only in capital letters")
        }
        val line = CanonicalJson.encode(entry).toByteArray(Charsets.UTF_8)
        if (line.size > limits.entryLineBytes) fail(BackupProblem.TOO_LARGE, "Entry ${entry.id} is larger than ${limits.entryLineBytes} bytes")
        count(line.size + 1L)
        zip.write(line)
        zip.write(NEWLINE)
        entriesDigest.update(line)
        entriesDigest.update(NEWLINE.toByte())
        entriesBytes += line.size + 1
        if (entry.photo?.missing != null) missingPhotos++
        entryCount++
        lastId = entry.id
    }

    /**
     * Stores the photo [open] gives as `photos/[name]`. [open] is called twice,
     * once to measure and hash the photo and once to copy it, because a stored
     * file's size and CRC-32 come before its data; both must give the same
     * bytes. The writer closes each stream it opens.
     */
    fun writePhoto(name: String, open: () -> InputStream) {
        if (stage == Stage.ENTRIES) step(Stage.ENTRIES) { endEntries() }
        step(Stage.PHOTOS) {
            if (!BackupRules.isPhotoName(name)) fail(BackupProblem.UNSAFE_NAME, "\"${BackupRules.printable(name)}\" isn't a name a backup allows")
            val use = usedPhotos[name]?.takeIf { it.name == name } ?: fail(BackupProblem.INCONSISTENT, "No entry includes photo \"$name\"")
            lastPhoto?.let {
                if (name == it) fail(BackupProblem.DUPLICATE_FILE, "Photo \"$name\" was already written")
                if (name < it) fail(BackupProblem.INCONSISTENT, "Photo \"$name\" comes after \"$it\": photos go in ascending name order")
            }
            // settings.json, entries.jsonl, the photos so far, this one and manifest.json.
            if (writtenPhotos + 4 > limits.files) fail(BackupProblem.TOO_LARGE, "A backup holds at most ${limits.files} files")

            val measured = measure(name, open)
            count(measured.size)
            zip.putNextEntry(zipEntry(BackupFormat.PHOTOS_DIR + name).apply {
                method = ZipEntry.STORED
                size = measured.size
                compressedSize = measured.size
                crc = measured.crc
            })
            var copiedSize = 0L
            val copied = measure(name, open) { b, n ->
                copiedSize += n
                if (copiedSize > measured.size) fail(BackupProblem.INCONSISTENT, "Photo \"$name\" changed while it was being backed up")
                zip.write(b, 0, n)
            }
            if (copied != measured) fail(BackupProblem.INCONSISTENT, "Photo \"$name\" changed while it was being backed up")
            zip.closeEntry()
            files += BackupFile(BackupFormat.PHOTOS_DIR + name, measured.size, measured.sha256)
            use.written = true
            writtenPhotos++
            lastPhoto = name
        }
    }

    private data class Measured(val size: Long, val crc: Long, val sha256: String)

    /** Reads a photo through once, within the size limit, handing each chunk to [each]. */
    private inline fun measure(name: String, open: () -> InputStream, each: (ByteArray, Int) -> Unit = { _, _ -> }): Measured {
        val digest = sha256()
        val crc = CRC32()
        var size = 0L
        open().use { input ->
            copy(input) { b, n ->
                size += n
                if (size > limits.photoBytes) fail(BackupProblem.TOO_LARGE, "Photo \"$name\" is larger than ${limits.photoBytes} bytes")
                digest.update(b, 0, n)
                crc.update(b, 0, n)
                each(b, n)
            }
        }
        return Measured(size, crc.value, hex(digest))
    }

    /**
     * Writes the manifest and completes the ZIP. [idHighWaterMark] is SQLite's
     * AUTOINCREMENT counter for the entries table, or null if it has none.
     */
    fun finish(idHighWaterMark: Long?): BackupManifest {
        if (stage == Stage.ENTRIES) step(Stage.ENTRIES) { endEntries() }
        return step(Stage.PHOTOS) {
            usedPhotos.values.firstOrNull { !it.written }?.let {
                fail(BackupProblem.MISSING_FILE, "An entry includes photo \"${it.name}\", but it wasn't written")
            }
            if (idHighWaterMark != null) {
                if (idHighWaterMark < 0) fail(BackupProblem.INCONSISTENT, "The ID counter $idHighWaterMark is negative, which SQLite never makes it")
                lastId?.let { if (it > idHighWaterMark) fail(BackupProblem.INCONSISTENT, "The ID counter $idHighWaterMark is below entry ID $it") }
            }
            val manifest = BackupManifest(
                format = BackupFormat.FORMAT_ID,
                formatVersion = BackupFormat.VERSION,
                createdAt = createdAt,
                app = app,
                databaseVersion = databaseVersion,
                entryCount = entryCount,
                photoCount = writtenPhotos,
                missingPhotoCount = missingPhotos,
                idHighWaterMark = idHighWaterMark,
                files = files.toList(),
            )
            BackupRules.manifestProblem(manifest, limits)?.let { fail(BackupProblem.INVALID_MANIFEST, it) }
            // Measured first, so a manifest over the limit is refused before any of it is written.
            var size = 0L
            CanonicalJson.encode(manifest) { size += it.toByteArray(Charsets.UTF_8).size }
            if (size > limits.manifestBytes) fail(BackupProblem.TOO_LARGE, "The manifest is larger than ${limits.manifestBytes} bytes")
            count(size)
            zip.putNextEntry(zipEntry(BackupFormat.MANIFEST))
            CanonicalJson.encode(manifest) { zip.write(it.toByteArray(Charsets.UTF_8)) }
            zip.closeEntry()
            zip.finish()
            zip.flush()
            stage = Stage.DONE
            manifest
        }
    }

    /** Closes [output]. */
    override fun close() {
        zip.close()
    }

    private fun endEntries() {
        zip.closeEntry()
        files += BackupFile(BackupFormat.ENTRIES, entriesBytes, hex(entriesDigest))
        stage = Stage.PHOTOS
    }

    private fun writeWhole(path: String, bytes: ByteArray) {
        count(bytes.size.toLong())
        zip.putNextEntry(zipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
        files += BackupFile(path, bytes.size.toLong(), hex(sha256().apply { update(bytes) }))
    }

    private fun count(bytes: Long) {
        totalBytes += bytes
        if (totalBytes > limits.totalBytes) fail(BackupProblem.TOO_LARGE, "The backup would be larger than ${limits.totalBytes} bytes")
    }

    private inline fun <T> step(expected: Stage, block: () -> T): T {
        check(stage == expected) {
            if (stage == Stage.FAILED) "BackupWriter can't continue after a failed step" else "BackupWriter called out of order: it is at $stage"
        }
        try {
            return block()
        } catch (e: Throwable) {
            stage = Stage.FAILED
            throw e
        }
    }

    private companion object {
        const val NEWLINE = '\n'.code

        /**
         * One fixed time for every file. A real one would be in local time,
         * and outside 1980-2107 (a phone with its clock reset) ZipOutputStream
         * would add an extra field. The manifest's createdAt is the real time.
         */
        val ENTRY_TIME: Long = GregorianCalendar(2000, Calendar.JANUARY, 1).timeInMillis

        fun zipEntry(name: String) = ZipEntry(name).apply { time = ENTRY_TIME }
    }
}
