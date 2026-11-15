package com.evergreen.trackora.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.BitSet

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
                val index = archive.photoIndex(name)
                if (index < 0) return PhotoExtraction.Refused(BackupProblem.MISSING_FILE, "The backup has no photo \"$name\"")
                val record = archive.photoRecords[index]
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
                        archive.openPhoto(index).use { input ->
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
    /** The photos entries use, by index among the photo records. */
    val usedPhotos: BitSet,
)

/**
 * A backup whose container and manifest have passed, and the files they
 * describe.
 *
 * File i of the archive is record i of the manifest (the order is checked),
 * so files are found by position, and photos, which are in ascending name
 * order, by binary search: no maps or sets of names are kept, and each name
 * is held once, shared by the archive item and the manifest record.
 */
internal class Archive private constructor(
    private val raf: RandomAccessFile,
    private val limits: Limits,
    val manifest: BackupManifest,
    private val items: List<ZipItem>,
) {
    /** The manifest's records of the photos, which follow settings.json and entries.jsonl. */
    val photoRecords: List<BackupFile> = manifest.files.subList(2, manifest.files.size)

    /** The index of photo [name] among [photoRecords], or -1. */
    fun photoIndex(name: String): Int {
        var low = 0
        var high = photoRecords.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val order = comparePhotoName(photoRecords[middle].path, name)
            when {
                order < 0 -> low = middle + 1
                order > 0 -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }

    fun openPhoto(index: Int): InputStream = open(index + 2)

    private fun open(index: Int): InputStream = ZipLayout.open(raf, items[index])

    fun readSettings(): BackupSettings {
        val bytes = readWhole(0)
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
        val record = manifest.files[1]
        val digest = sha256()
        val line = ByteArrayOutputStream()
        var lineNumber = 0L
        var problem: BackupException? = null
        var count = 0L
        var missing = 0L
        var lastId: Long? = null
        val used = BitSet(photoRecords.size)

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
                val index = photoIndex(name)
                if (index < 0) {
                    throw BackupException(BackupProblem.MISSING_FILE, "Entry ${entry.id} uses photo \"$name\", which isn't in the backup")
                }
                used.set(index)
            }
            if (entry.photo?.missing != null) missing++
            count++
            lastId = entry.id
            onEntry?.invoke(entry)
        }

        open(1).use { input ->
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
        val unused = entries.usedPhotos.nextClearBit(0)
        if (unused < photoRecords.size) {
            fail(BackupProblem.INCONSISTENT, "${photoRecords[unused].path} isn't used by any entry")
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
        for ((index, record) in photoRecords.withIndex()) {
            val digest = sha256()
            openPhoto(index).use { input -> copy(input) { b, n -> digest.update(b, 0, n) } }
            if (hex(digest) != record.sha256) {
                fail(BackupProblem.HASH_MISMATCH, "${record.path} doesn't match its SHA-256 in the manifest")
            }
        }
    }

    /** File [index], which [open] has already bounded to a small limit. */
    private fun readWhole(index: Int): ByteArray {
        val record = manifest.files[index]
        val out = ByteArrayOutputStream()
        open(index).use { input -> copy(input) { b, n -> out.write(b, 0, n) } }
        val bytes = out.toByteArray()
        if (hex(sha256().apply { update(bytes) }) != record.sha256) {
            fail(BackupProblem.HASH_MISMATCH, "${record.path} doesn't match its SHA-256 in the manifest")
        }
        return bytes
    }

    companion object {
        private const val NEWLINE = '\n'.code.toByte()

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

            val byName = HashMap<String, ZipItem>(items.size * 2)
            for (item in items) byName.putIfAbsent(item.name, item)
            val manifest = readManifest(raf, manifestItem, limits) { path -> byName[path]?.name ?: path }

            checkNames(items)

            val listed = manifest.files
            val inOrder = items.size == listed.size + 1 && items.last().name == BackupFormat.MANIFEST &&
                listed.indices.all { listed[it].path == items[it].name }
            if (!inOrder) {
                listed.firstOrNull { byName[it.path] == null }?.let {
                    fail(BackupProblem.MISSING_FILE, "${it.path} is listed in the manifest but isn't in the backup")
                }
                val listedPaths = listed.mapTo(HashSet(listed.size * 2)) { it.path }
                items.firstOrNull { it.name != BackupFormat.MANIFEST && it.name !in listedPaths }?.let {
                    fail(BackupProblem.UNEXPECTED_FILE, "${BackupRules.printable(it.name)} isn't listed in the manifest")
                }
                fail(BackupProblem.INCONSISTENT, "The files aren't in the order the format and the manifest give")
            }

            for ((index, record) in listed.withIndex()) {
                val size = items[index].size
                when {
                    index == 0 && size > limits.settingsBytes ->
                        fail(BackupProblem.TOO_LARGE, "settings.json is larger than ${limits.settingsBytes} bytes")
                    index >= 2 && size > limits.photoBytes ->
                        fail(BackupProblem.TOO_LARGE, "${record.path} is larger than ${limits.photoBytes} bytes")
                }
            }
            for ((index, record) in listed.withIndex()) {
                val size = items[index].size
                if (size != record.size) fail(BackupProblem.HASH_MISMATCH, "${record.path} is $size bytes; the manifest says ${record.size}")
            }
            return Archive(raf, limits, manifest, items)
        }

        /**
         * Parses the manifest as it unpacks. If the parse fails, the rest is
         * still read, so a damaged manifest is reported as damaged rather than
         * as the parse error the damage caused.
         */
        private fun readManifest(raf: RandomAccessFile, item: ZipItem, limits: Limits, intern: (String) -> String): BackupManifest =
            ZipLayout.open(raf, item).use { input ->
                val parsed = try {
                    Result.success(ManifestParser.parse(input, limits, intern))
                } catch (e: BackupException) {
                    if (e.problem == BackupProblem.DAMAGED) throw e
                    Result.failure(e)
                }
                copy(input) { _, _ -> }
                val manifest = parsed.getOrThrow()
                BackupRules.manifestProblem(manifest, limits)?.let { fail(BackupProblem.INVALID_MANIFEST, "manifest.json: $it") }
                manifest
            }

        /** Unsafe names first, then duplicates, then names the format doesn't have. */
        private fun checkNames(items: List<ZipItem>) {
            fun allowed(name: String) =
                name == BackupFormat.MANIFEST || name == BackupFormat.SETTINGS || name == BackupFormat.ENTRIES ||
                    BackupRules.photoNameOf(name) != null

            items.firstOrNull { !allowed(it.name) && BackupRules.isUnsafePath(it.name) }?.let {
                fail(BackupProblem.UNSAFE_NAME, "A file is named \"${BackupRules.printable(it.name)}\", which could reach outside its folder")
            }
            // Sorting ignoring case puts names that differ only in case side by side.
            val names = items.mapTo(ArrayList(items.size)) { it.name }
            names.sortWith(String.CASE_INSENSITIVE_ORDER)
            for (i in 1 until names.size) {
                if (names[i].equals(names[i - 1], ignoreCase = true)) {
                    fail(BackupProblem.DUPLICATE_FILE, "\"${BackupRules.printable(names[i])}\" appears more than once, ignoring capital letters")
                }
            }
            items.firstOrNull { !allowed(it.name) }?.let {
                fail(BackupProblem.UNEXPECTED_FILE, "\"${BackupRules.printable(it.name)}\" isn't part of a Trackora backup")
            }
        }

        /** [path] (`photos/` and a name) against photo [name], in String.compareTo order. */
        private fun comparePhotoName(path: String, name: String): Int {
            val offset = BackupFormat.PHOTOS_DIR.length
            val common = minOf(path.length - offset, name.length)
            for (i in 0 until common) {
                val difference = path[offset + i] - name[i]
                if (difference != 0) return difference
            }
            return (path.length - offset) - name.length
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
