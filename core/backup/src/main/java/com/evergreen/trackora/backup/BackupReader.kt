package com.evergreen.trackora.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** The outcome of checking a backup file. */
sealed interface BackupCheck {
    /** Every check passed. */
    data class Valid(val backup: VerifiedBackup) : BackupCheck

    /** Refused, for [problem]. [detail] says exactly what, in English, for logs and tests. */
    data class Invalid(val problem: BackupProblem, val detail: String) : BackupCheck
}

/** What a valid backup holds, apart from the entries and photo bytes, which are read on demand. */
data class VerifiedBackup(
    val manifest: BackupManifest,
    val settings: BackupSettings,
    /** The `photos/` files, with their sizes and SHA-256s. */
    val photos: List<BackupFile>,
)

/** The outcome of unpacking one photo. */
sealed interface PhotoExtraction {
    data class Extracted(val file: File) : PhotoExtraction

    /** A file of that name is already there. It was left exactly as it was. */
    data class TargetExists(val file: File) : PhotoExtraction

    data class Refused(val problem: BackupProblem, val detail: String) : PhotoExtraction
}

/**
 * Checks and reads backup files, accepting exactly what [BackupFormat]
 * defines.
 *
 * A file is read through its own [RandomAccessFile] for each call, so the
 * caller passes a local file: restore copies the chosen backup into private
 * staging first, and that copy is what gets checked. The reader never writes
 * anything except the one photo [extractPhoto] is asked for.
 *
 * Each call checks the container and manifest again, and every file it reads
 * against its CRC-32 and SHA-256, so a later call can't be fooled by a file
 * changed after an earlier one.
 *
 * Only an I/O error reading [file] itself is thrown; every problem with its
 * contents comes back as [BackupCheck.Invalid] or [PhotoExtraction.Refused].
 */
object BackupReader {

    /** Checks everything: container, manifest, settings, every entry and every photo. */
    fun verify(file: File): BackupCheck = check(file, Limits.V1, null)

    /**
     * Like [verify], handing each entry to [onEntry] as it is read, in ID
     * order. The entries are provisional until this returns [BackupCheck.Valid]:
     * a problem later in the file means none of them may be used.
     */
    fun readEntries(file: File, onEntry: (BackupEntry) -> Unit): BackupCheck = check(file, Limits.V1, onEntry)

    /**
     * Unpacks the photo [name] into [targetDir], which must exist, checking its
     * size, CRC-32 and SHA-256 on the way. Never replaces an existing file:
     * the new file is created only if nothing of that name is there. If
     * anything fails, the file this call created is deleted again.
     */
    fun extractPhoto(file: File, name: String, targetDir: File): PhotoExtraction =
        extract(file, name, targetDir, Limits.V1)

    internal fun check(file: File, limits: Limits, onEntry: ((BackupEntry) -> Unit)?): BackupCheck = try {
        RandomAccessFile(file, "r").use { raf ->
            val archive = Archive.open(raf, limits)
            val settings = archive.readSettings()
            val entries = archive.readEntries(onEntry)
            archive.checkAgainst(entries)
            archive.checkPhotos()
            BackupCheck.Valid(VerifiedBackup(archive.manifest, settings, archive.photoRecords))
        }
    } catch (e: BackupException) {
        BackupCheck.Invalid(e.problem, e.message.orEmpty())
    }

    internal fun extract(file: File, name: String, targetDir: File, limits: Limits): PhotoExtraction {
        if (!BackupRules.isPhotoName(name)) {
            return PhotoExtraction.Refused(BackupProblem.UNSAFE_NAME, "\"${BackupRules.printable(name)}\" isn't a photo name")
        }
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val archive = Archive.open(raf, limits)
                val record = archive.photoRecord(name)
                    ?: return PhotoExtraction.Refused(BackupProblem.MISSING_FILE, "The backup has no photo \"$name\"")
                val dir = targetDir.canonicalFile
                val target = File(dir, name)
                if (target.canonicalFile.parentFile != dir) {
                    return PhotoExtraction.Refused(BackupProblem.UNSAFE_NAME, "\"$name\" would land outside ${dir.path}")
                }
                if (!target.createNewFile()) return PhotoExtraction.TargetExists(target)
                var kept = false
                try {
                    val digest = sha256()
                    FileOutputStream(target).use { out ->
                        archive.open(record.path).use { input ->
                            copy(input) { b, n ->
                                digest.update(b, 0, n)
                                out.write(b, 0, n)
                            }
                        }
                        out.flush()
                        out.fd.sync()
                    }
                    if (hex(digest) != record.sha256) {
                        throw BackupException(BackupProblem.HASH_MISMATCH, "photos/$name doesn't match its SHA-256 in the manifest")
                    }
                    kept = true
                    PhotoExtraction.Extracted(target)
                } finally {
                    if (!kept) target.delete()
                }
            }
        } catch (e: BackupException) {
            PhotoExtraction.Refused(e.problem, e.message.orEmpty())
        }
    }
}

/** What reading `entries.jsonl` found, for the checks that need every entry. */
internal class EntryTally(
    val count: Long,
    val missingPhotos: Long,
    val highestId: Long?,
    val usedPhotos: Set<String>,
)

/** A backup whose container and manifest have passed, and the files they describe. */
internal class Archive private constructor(
    private val raf: RandomAccessFile,
    private val limits: Limits,
    val manifest: BackupManifest,
    private val items: Map<String, ZipItem>,
) {
    private val records = manifest.files.associateBy { it.path }

    val photoRecords: List<BackupFile> = manifest.files.drop(2)
    private val photoNames: Set<String> = photoRecords.mapTo(HashSet()) { BackupRules.photoNameOf(it.path)!! }

    fun photoRecord(name: String): BackupFile? = records[BackupFormat.PHOTOS_DIR + name]

    fun open(path: String): InputStream = ZipLayout.open(raf, items.getValue(path))

    fun readSettings(): BackupSettings {
        val record = records.getValue(BackupFormat.SETTINGS)
        val bytes = readWhole(record)
        val text = CanonicalJson.utf8(bytes) ?: fail(BackupProblem.INVALID_SETTINGS, "settings.json isn't UTF-8 text")
        val settings = try {
            CanonicalJson.decodeSettings(text)
        } catch (e: IllegalArgumentException) {
            fail(BackupProblem.INVALID_SETTINGS, "settings.json: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (CanonicalJson.encode(settings) != text) fail(BackupProblem.INVALID_SETTINGS, "settings.json isn't written the way the format requires")
        BackupRules.settingsProblem(settings)?.let { fail(BackupProblem.INVALID_SETTINGS, "settings.json: $it") }
        return settings
    }

    /**
     * Streams `entries.jsonl`, checking each line. Reads to the end even after
     * a bad line, so damage (CRC-32, SHA-256) is reported in preference to the
     * bad line it probably caused.
     */
    fun readEntries(onEntry: ((BackupEntry) -> Unit)?): EntryTally {
        val record = records.getValue(BackupFormat.ENTRIES)
        val digest = sha256()
        val line = ByteArrayOutputStream()
        var lineNumber = 0L
        var problem: BackupException? = null
        var count = 0L
        var missing = 0L
        var lastId: Long? = null
        val used = HashSet<String>()

        fun take(bytes: ByteArray) {
            lineNumber++
            val text = CanonicalJson.utf8(bytes) ?: throw entryProblem(lineNumber, "isn't UTF-8 text")
            val entry = try {
                CanonicalJson.decodeEntry(text)
            } catch (e: IllegalArgumentException) {
                throw entryProblem(lineNumber, e.message?.lineSequence()?.firstOrNull().orEmpty())
            }
            if (CanonicalJson.encode(entry) != text) throw entryProblem(lineNumber, "isn't written the way the format requires")
            BackupRules.entryProblem(entry)?.let { throw entryProblem(lineNumber, it) }
            lastId?.let { if (entry.id <= it) throw entryProblem(lineNumber, "ID ${entry.id} comes after $it: IDs must ascend, each once") }
            entry.photo?.file?.let { name ->
                if (name !in photoNames) {
                    throw BackupException(BackupProblem.MISSING_FILE, "Entry ${entry.id} uses photo \"$name\", which isn't in the backup")
                }
                used += name
            }
            if (entry.photo?.missing != null) missing++
            count++
            lastId = entry.id
            onEntry?.invoke(entry)
        }

        open(BackupFormat.ENTRIES).use { input ->
            copy(input) { b, n ->
                digest.update(b, 0, n)
                if (problem != null) return@copy
                try {
                    var start = 0
                    for (i in 0 until n) {
                        if (b[i] == NEWLINE) {
                            append(line, b, start, i - start, lineNumber + 1)
                            take(line.toByteArray())
                            line.reset()
                            start = i + 1
                        }
                    }
                    append(line, b, start, n - start, lineNumber + 1)
                } catch (e: BackupException) {
                    problem = e
                }
            }
        }
        if (hex(digest) != record.sha256) {
            fail(BackupProblem.HASH_MISMATCH, "entries.jsonl doesn't match its SHA-256 in the manifest")
        }
        problem?.let { throw it }
        if (line.size() > 0) throw entryProblem(lineNumber + 1, "has no line break: the last line is incomplete")
        return EntryTally(count, missing, lastId, used)
    }

    private fun append(line: ByteArrayOutputStream, b: ByteArray, start: Int, length: Int, lineNumber: Long) {
        if (line.size().toLong() + length > limits.entryLineBytes) {
            fail(BackupProblem.TOO_LARGE, "entries.jsonl line $lineNumber is longer than ${limits.entryLineBytes} bytes")
        }
        line.write(b, start, length)
    }

    /** Checks that agree with the manifest only once every entry has been read. */
    fun checkAgainst(entries: EntryTally) {
        photoNames.sorted().firstOrNull { it !in entries.usedPhotos }?.let {
            fail(BackupProblem.INCONSISTENT, "photos/$it isn't used by any entry")
        }
        if (entries.count != manifest.entryCount) {
            fail(BackupProblem.INCONSISTENT, "The manifest says ${manifest.entryCount} entries; entries.jsonl has ${entries.count}")
        }
        if (entries.missingPhotos != manifest.missingPhotoCount) {
            fail(BackupProblem.INCONSISTENT, "The manifest says ${manifest.missingPhotoCount} missing photos; the entries have ${entries.missingPhotos}")
        }
        val mark = manifest.idHighWaterMark
        val highest = entries.highestId
        if (mark != null && highest != null && highest > mark) {
            fail(BackupProblem.INCONSISTENT, "The ID counter $mark is below entry ID $highest, which SQLite never allows")
        }
    }

    fun checkPhotos() {
        for (record in photoRecords) {
            val digest = sha256()
            open(record.path).use { input -> copy(input) { b, n -> digest.update(b, 0, n) } }
            if (hex(digest) != record.sha256) {
                fail(BackupProblem.HASH_MISMATCH, "${record.path} doesn't match its SHA-256 in the manifest")
            }
        }
    }

    private fun readWhole(record: BackupFile): ByteArray {
        val out = ByteArrayOutputStream(record.size.toInt())
        open(record.path).use { input -> copy(input) { b, n -> out.write(b, 0, n) } }
        val bytes = out.toByteArray()
        if (hex(sha256().apply { update(bytes) }) != record.sha256) {
            fail(BackupProblem.HASH_MISMATCH, "${record.path} doesn't match its SHA-256 in the manifest")
        }
        return bytes
    }

    companion object {
        private const val NEWLINE = '\n'.code.toByte()
        private const val MANIFEST_DEPTH = 3

        /**
         * Checks the container, finds and checks the manifest, and checks that
         * the archive holds exactly the files it lists, at the sizes it lists,
         * within the limits. Reads only the manifest's data.
         */
        fun open(raf: RandomAccessFile, limits: Limits): Archive {
            val items = ZipLayout.read(raf, limits)

            val total = items.sumOf { it.size }
            if (total > limits.totalBytes) fail(BackupProblem.TOO_LARGE, "The backup unpacks to $total bytes; the limit is ${limits.totalBytes}")

            val manifests = items.filter { it.name == BackupFormat.MANIFEST }
            if (manifests.isEmpty()) fail(BackupProblem.NOT_A_TRACKORA_BACKUP, "There's no manifest.json")
            if (manifests.size > 1) fail(BackupProblem.DUPLICATE_FILE, "manifest.json appears more than once")
            val manifestItem = manifests.single()
            if (manifestItem.size > limits.manifestBytes) fail(BackupProblem.TOO_LARGE, "manifest.json is larger than ${limits.manifestBytes} bytes")
            val manifest = readManifest(raf, manifestItem, limits)

            checkNames(items)

            val listed = manifest.files.map { it.path }
            val listedSet = listed.toHashSet()
            val contained = items.map { it.name }.filter { it != BackupFormat.MANIFEST }
            val containedSet = contained.toHashSet()
            listed.firstOrNull { it !in containedSet }?.let { fail(BackupProblem.MISSING_FILE, "$it is listed in the manifest but isn't in the backup") }
            contained.firstOrNull { it !in listedSet }?.let { fail(BackupProblem.UNEXPECTED_FILE, "${BackupRules.printable(it)} isn't listed in the manifest") }
            if (items.last().name != BackupFormat.MANIFEST || contained != listed) {
                fail(BackupProblem.INCONSISTENT, "The files aren't in the order the format and the manifest give")
            }

            val byName = items.associateBy { it.name }
            for (record in manifest.files) {
                val size = byName.getValue(record.path).size
                when {
                    record.path == BackupFormat.SETTINGS && size > limits.settingsBytes ->
                        fail(BackupProblem.TOO_LARGE, "settings.json is larger than ${limits.settingsBytes} bytes")
                    record.path.startsWith(BackupFormat.PHOTOS_DIR) && size > limits.photoBytes ->
                        fail(BackupProblem.TOO_LARGE, "${record.path} is larger than ${limits.photoBytes} bytes")
                }
            }
            for (record in manifest.files) {
                val size = byName.getValue(record.path).size
                if (size != record.size) fail(BackupProblem.HASH_MISMATCH, "${record.path} is $size bytes; the manifest says ${record.size}")
            }
            return Archive(raf, limits, manifest, byName)
        }

        private fun readManifest(raf: RandomAccessFile, item: ZipItem, limits: Limits): BackupManifest {
            val out = ByteArrayOutputStream(item.size.toInt())
            ZipLayout.open(raf, item).use { input -> copy(input) { b, n -> out.write(b, 0, n) } }
            val text = CanonicalJson.utf8(out.toByteArray())
                ?: fail(BackupProblem.NOT_A_TRACKORA_BACKUP, "manifest.json isn't UTF-8 text")
            // A manifest nests three deep. Refusing deeper text up front keeps the
            // version check, which skips unknown values, from spending memory on it.
            if (CanonicalJson.nestsDeeperThan(text, MANIFEST_DEPTH)) {
                fail(BackupProblem.NOT_A_TRACKORA_BACKUP, "manifest.json isn't a Trackora backup manifest")
            }

            val version = try {
                CanonicalJson.decodeVersion(text)
            } catch (e: IllegalArgumentException) {
                fail(BackupProblem.NOT_A_TRACKORA_BACKUP, "manifest.json isn't a Trackora backup manifest")
            }
            val formatVersion = version.formatVersion
            if (version.format != BackupFormat.FORMAT_ID || formatVersion == null) {
                fail(BackupProblem.NOT_A_TRACKORA_BACKUP, "manifest.json isn't a Trackora backup manifest")
            }
            if (formatVersion > BackupFormat.VERSION) {
                fail(BackupProblem.NEWER_FORMAT, "The backup is in format $formatVersion, from a newer version of Trackora; this one reads format ${BackupFormat.VERSION}")
            }

            val manifest = try {
                CanonicalJson.decodeManifest(text)
            } catch (e: IllegalArgumentException) {
                fail(BackupProblem.INVALID_MANIFEST, "manifest.json: ${e.message?.lineSequence()?.firstOrNull()}")
            }
            if (CanonicalJson.encode(manifest) != text) fail(BackupProblem.INVALID_MANIFEST, "manifest.json isn't written the way the format requires")
            BackupRules.manifestProblem(manifest, limits)?.let {
                fail(BackupProblem.INVALID_MANIFEST, "manifest.json: $it")
            }
            return manifest
        }

        /** Unsafe names first, then duplicates, then names the format doesn't have. */
        private fun checkNames(items: List<ZipItem>) {
            fun allowed(name: String) =
                name == BackupFormat.MANIFEST || name == BackupFormat.SETTINGS || name == BackupFormat.ENTRIES ||
                    BackupRules.photoNameOf(name) != null

            items.firstOrNull { !allowed(it.name) && BackupRules.isUnsafePath(it.name) }?.let {
                fail(BackupProblem.UNSAFE_NAME, "A file is named \"${BackupRules.printable(it.name)}\", which could reach outside its folder")
            }
            val seen = HashSet<String>()
            items.firstOrNull { !seen.add(it.name.lowercase()) }?.let {
                fail(BackupProblem.DUPLICATE_FILE, "\"${BackupRules.printable(it.name)}\" appears more than once, ignoring capital letters")
            }
            items.firstOrNull { !allowed(it.name) }?.let {
                fail(BackupProblem.UNEXPECTED_FILE, "\"${BackupRules.printable(it.name)}\" isn't part of a Trackora backup")
            }
        }
    }
}

private fun entryProblem(line: Long, what: String) =
    BackupException(BackupProblem.INVALID_ENTRY, "entries.jsonl line $line: $what")

internal fun fail(problem: BackupProblem, detail: String): Nothing = throw BackupException(problem, detail)

internal fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

internal fun hex(digest: MessageDigest): String = hex(digest.digest())

internal fun hex(bytes: ByteArray): String {
    val digits = "0123456789abcdef"
    val out = CharArray(bytes.size * 2)
    bytes.forEachIndexed { i, b ->
        out[i * 2] = digits[(b.toInt() shr 4) and 0xF]
        out[i * 2 + 1] = digits[b.toInt() and 0xF]
    }
    return String(out)
}

/** Reads [input] to its end in chunks, handing each to [block]. */
internal inline fun copy(input: InputStream, block: (ByteArray, Int) -> Unit) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return
        if (n > 0) block(buffer, n)
    }
}
