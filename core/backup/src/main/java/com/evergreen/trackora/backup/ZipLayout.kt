package com.evergreen.trackora.backup

import java.io.EOFException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** One file of a checked archive: where its data is and what it must unpack to. */
internal class ZipItem(
    /** The name's bytes as ISO-8859-1, so every byte maps to one character and nothing is lost. */
    val name: String,
    val method: Int,
    val flags: Int,
    val crc: Long,
    val compressedSize: Long,
    val size: Long,
    val localOffset: Long,
) {
    /** Where the file's data starts; set once its local header has been checked. */
    var dataOffset: Long = -1
        internal set
}

/**
 * Reads the structure of a backup's ZIP container and refuses anything outside
 * the subset [BackupFormat] allows.
 *
 * This is deliberately not `java.util.zip.ZipFile`. ZipFile on the JVM lists
 * duplicate names, opens archives with data before or after them, ignores
 * gaps and overlaps between files, and doesn't check CRC-32 when reading;
 * Android's version differs from it in places (it refuses duplicates, and
 * on Android 14+ some names). Parsing the container here gives one behaviour
 * on every Android version and on the JVM, and proves the layout is the
 * single unambiguous one the writer produces: files back to back from byte 0,
 * each local header agreeing with the central directory, the central directory
 * right after the last file, the end record at the very end.
 *
 * Only the header fields ZipOutputStream writes (on the JVM and on Android
 * 7-16 alike) are accepted. Anything else, such as extra fields (Unicode
 * paths, timestamps, ZIP64), Unix attributes (permissions, symbolic links) or
 * other flags, is refused: other tools act on those fields, so accepting them
 * would let one archive mean different things to different programs.
 *
 * Memory stays small whatever the file claims: the central directory is read
 * through a 64 KiB buffer, after its size has been bounded by what [total]
 * names of at most [MAX_NAME_BYTES] can need, and each name is refused before
 * it is copied if it is longer than any a backup uses.
 *
 * Unpacking uses Android's own zlib through [Inflater].
 */
internal object ZipLayout {

    const val STORED = 0
    const val DEFLATED = 8

    private const val LOCAL_SIGNATURE = 0x04034b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val END_SIGNATURE = 0x06054b50L
    private const val DESCRIPTOR_SIGNATURE = 0x08074b50L

    private const val LOCAL_SIZE = 30
    private const val CENTRAL_SIZE = 46
    private const val END_SIZE = 22
    private const val DESCRIPTOR_SIZE = 16

    private const val FLAG_ENCRYPTED = 0x0001
    private const val FLAG_DESCRIPTOR = 0x0008
    private const val FLAG_UTF8 = 0x0800
    private const val FLAG_STRONG_ENCRYPTION = 0x0040

    /**
     * The only flags ZipOutputStream writes: UTF-8 names, plus a data
     * descriptor on a deflated file (whose CRC-32 and sizes follow its data).
     */
    private const val STORED_FLAGS = FLAG_UTF8
    private const val DEFLATED_FLAGS = FLAG_UTF8 or FLAG_DESCRIPTOR

    /** "Version needed", and equally "version made by" with the MS-DOS host byte 0, as ZipOutputStream writes them. */
    private const val STORED_VERSION = 10
    private const val DEFLATED_VERSION = 20

    /** The longest name a backup has: `photos/` and a 128-character photo name. */
    val MAX_NAME_BYTES = BackupFormat.PHOTOS_DIR.length + BackupFormat.MAX_PHOTO_NAME_LENGTH

    /** A central-directory record with the longest name and nothing else. */
    private val MAX_CENTRAL_RECORD = CENTRAL_SIZE + MAX_NAME_BYTES

    fun read(raf: RandomAccessFile, limits: Limits): List<ZipItem> {
        val length = raf.length()
        // Every ZIP record starts "PK": a file that does is a damaged ZIP, not some other file.
        val looksLikeZip = readAt(raf, 0, 2, length)?.let { it[0] == 'P'.code.toByte() && it[1] == 'K'.code.toByte() } == true

        fun broken(why: String): Nothing =
            if (looksLikeZip) throw BackupException(BackupProblem.DAMAGED, "The file is incomplete or damaged: $why")
            else throw BackupException(BackupProblem.NOT_A_ZIP, "This isn't a ZIP file")

        fun unsupported(what: String): Nothing =
            throw BackupException(BackupProblem.UNSUPPORTED_ZIP, "The ZIP uses $what, which a Trackora backup never does")

        if (length < END_SIZE) broken("it is too short to be a ZIP file")
        if (length > 0xFFFFFFFFL) throw BackupException(BackupProblem.TOO_LARGE, "The file is 4 GiB or larger")

        val end = readAt(raf, length - END_SIZE, END_SIZE, length) ?: broken("its end can't be read")
        if (u32(end, 0) != END_SIGNATURE) broken("it doesn't end with a ZIP end record")
        val disk = u16(end, 4)
        val centralDisk = u16(end, 6)
        val onThisDisk = u16(end, 8)
        val total = u16(end, 10)
        val centralSize = u32(end, 12)
        val centralOffset = u32(end, 16)
        if (total == 0xFFFF || centralSize == 0xFFFFFFFFL || centralOffset == 0xFFFFFFFFL) unsupported("ZIP64")
        if (disk != 0 || centralDisk != 0 || onThisDisk != total) unsupported("several disks")
        if (u16(end, 20) != 0) broken("its end record claims a comment that isn't there")
        if (total > limits.files) throw BackupException(BackupProblem.TOO_LARGE, "The backup holds $total files; the limit is ${limits.files}")
        if (centralOffset + centralSize != length - END_SIZE) broken("its central directory isn't where the end record says")
        // Both are below 2^32 and total is below 2^16, so these products can't overflow a Long.
        if (centralSize > total.toLong() * MAX_CENTRAL_RECORD) {
            unsupported("a central directory larger than $total file names can need (long names, extra fields or comments)")
        }

        val central = Region(raf, centralOffset, centralOffset + centralSize)
        val header = ByteArray(CENTRAL_SIZE)
        val items = ArrayList<ZipItem>(total)
        for (i in 1..total) {
            if (!central.read(header, CENTRAL_SIZE) || u32(header, 0) != CENTRAL_SIGNATURE) broken("central directory record $i is damaged")
            val versionMadeBy = u16(header, 4)
            val versionNeeded = u16(header, 6)
            val flags = u16(header, 8)
            val method = u16(header, 10)
            val crc = u32(header, 16)
            val compressedSize = u32(header, 20)
            val size = u32(header, 24)
            val nameLength = u16(header, 28)
            val extraLength = u16(header, 30)
            val commentLength = u16(header, 32)
            val diskStart = u16(header, 34)
            val internalAttributes = u16(header, 36)
            val externalAttributes = u32(header, 38)
            val localOffset = u32(header, 42)

            if (flags and (FLAG_ENCRYPTED or FLAG_STRONG_ENCRYPTION) != 0) unsupported("encryption")
            if (method != STORED && method != DEFLATED) unsupported("compression method $method")
            if (versionNeeded > DEFLATED_VERSION) unsupported("features of ZIP version ${versionNeeded / 10}.${versionNeeded % 10}")
            if (compressedSize == 0xFFFFFFFFL || size == 0xFFFFFFFFL || localOffset == 0xFFFFFFFFL || diskStart == 0xFFFF) unsupported("ZIP64")
            if (diskStart != 0) unsupported("several disks")
            if (extraLength != 0) unsupported("extra fields, such as Unicode paths, timestamps or ZIP64")
            if (commentLength != 0) unsupported("file comments")
            if (nameLength == 0) broken("a file has no name")
            if (nameLength > MAX_NAME_BYTES) {
                throw BackupException(BackupProblem.UNEXPECTED_FILE, "A file's name is $nameLength bytes, longer than any name in a Trackora backup")
            }
            if (method == STORED && flags and FLAG_DESCRIPTOR != 0) unsupported("a data descriptor on a stored file")
            if (flags != (if (method == STORED) STORED_FLAGS else DEFLATED_FLAGS)) unsupported("flags 0x${Integer.toHexString(flags)}")
            val version = if (method == STORED) STORED_VERSION else DEFLATED_VERSION
            if (versionNeeded != version || versionMadeBy != version) {
                unsupported("version fields 0x${Integer.toHexString(versionMadeBy)}/$versionNeeded, as written by other tools or systems")
            }
            if (internalAttributes != 0 || externalAttributes != 0L) unsupported("file attributes, such as permissions or a symbolic link")
            if (method == STORED && compressedSize != size) broken("a stored file's two sizes differ")

            val nameBytes = ByteArray(nameLength)
            if (!central.read(nameBytes, nameLength)) broken("central directory record $i runs past its end")
            items += ZipItem(String(nameBytes, Charsets.ISO_8859_1), method, flags, crc, compressedSize, size, localOffset)
        }
        if (central.position != centralOffset + centralSize) broken("its central directory has bytes after the last record")

        val files = Region(raf, 0, centralOffset)
        val local = ByteArray(LOCAL_SIZE)
        val descriptor = ByteArray(DESCRIPTOR_SIZE)
        var expected = 0L
        for (item in items) {
            if (item.localOffset != expected) broken("its files don't lie back to back")
            files.position = item.localOffset
            if (!files.read(local, LOCAL_SIZE) || u32(local, 0) != LOCAL_SIGNATURE) broken("a file header is damaged or runs into the central directory")
            val versionNeeded = u16(local, 4)
            val flags = u16(local, 6)
            val method = u16(local, 8)
            val crc = u32(local, 14)
            val compressedSize = u32(local, 18)
            val size = u32(local, 22)
            val nameLength = u16(local, 26)
            val extraLength = u16(local, 28)
            val hasDescriptor = item.flags and FLAG_DESCRIPTOR != 0
            if (versionNeeded != (if (item.method == STORED) STORED_VERSION else DEFLATED_VERSION) || flags != item.flags || method != item.method) {
                broken("a file header disagrees with the central directory")
            }
            // With a data descriptor, ZipOutputStream leaves these zero and writes them after the data.
            val agrees = if (hasDescriptor) crc == 0L && compressedSize == 0L && size == 0L
            else crc == item.crc && compressedSize == item.compressedSize && size == item.size
            if (!agrees) broken("a file header disagrees with the central directory")
            if (extraLength != 0) unsupported("extra fields, such as Unicode paths, timestamps or ZIP64")
            if (nameLength != item.name.length) broken("a file's two names differ")
            val nameBytes = ByteArray(nameLength)
            if (!files.read(nameBytes, nameLength) || !nameBytes.contentEquals(item.name.toByteArray(Charsets.ISO_8859_1))) {
                broken("a file's two names differ")
            }

            item.dataOffset = files.position
            files.position = item.dataOffset + item.compressedSize
            if (hasDescriptor) {
                if (!files.read(descriptor, DESCRIPTOR_SIZE)) broken("a file runs into the central directory")
                if (u32(descriptor, 0) != DESCRIPTOR_SIGNATURE || u32(descriptor, 4) != item.crc ||
                    u32(descriptor, 8) != item.compressedSize || u32(descriptor, 12) != item.size
                ) {
                    broken("a data descriptor disagrees with the central directory")
                }
            }
            if (files.position > centralOffset) broken("a file runs into the central directory")
            expected = files.position
        }
        if (expected != centralOffset) broken("there are bytes between the last file and the central directory")
        return items
    }

    /** Unpacks [item], checking it unpacks to exactly its recorded size and CRC-32. */
    fun open(raf: RandomAccessFile, item: ZipItem): InputStream = ItemInput(raf, item)

    /** Reads a stretch of the file through one small buffer. [position] can be moved freely. */
    private class Region(private val raf: RandomAccessFile, start: Long, private val end: Long) {
        private val buffer = ByteArray(64 * 1024)
        private var bufferStart = start
        private var bufferLength = 0
        var position = start

        /** Reads [count] bytes at [position] into [into], or returns false if they run past the end. */
        fun read(into: ByteArray, count: Int): Boolean {
            if (position < 0 || position + count > end) return false
            var copied = 0
            while (copied < count) {
                if (position < bufferStart || position >= bufferStart + bufferLength) {
                    bufferStart = position
                    bufferLength = minOf(buffer.size.toLong(), end - position).toInt()
                    raf.seek(position)
                    raf.readFully(buffer, 0, bufferLength)
                }
                val offset = (position - bufferStart).toInt()
                val n = minOf(count - copied, bufferLength - offset)
                System.arraycopy(buffer, offset, into, copied, n)
                copied += n
                position += n
            }
            return true
        }
    }

    private fun readAt(raf: RandomAccessFile, position: Long, count: Int, length: Long): ByteArray? {
        if (position < 0 || count < 0 || position + count > length) return null
        val bytes = ByteArray(count)
        return try {
            raf.seek(position)
            raf.readFully(bytes)
            bytes
        } catch (e: EOFException) {
            null
        }
    }

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, at: Int): Long =
        u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

    /**
     * An item's unpacked bytes. Never yields more than the recorded size, and
     * at the end throws [BackupProblem.DAMAGED] unless the data was exactly
     * the recorded size and CRC-32, with no compressed bytes left over.
     */
    private class ItemInput(private val raf: RandomAccessFile, private val item: ZipItem) : InputStream() {
        private val inflater = if (item.method == DEFLATED) Inflater(true) else null
        private val input = ByteArray(64 * 1024)
        private val crc = CRC32()
        private var fed = 0L
        private var produced = 0L
        private var dummyFed = false
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (finished) return -1
            if (len == 0) return 0
            val n = if (inflater == null) readStored(b, off, len) else readDeflated(inflater, b, off, len)
            if (n < 0) {
                finish()
                return -1
            }
            produced += n
            if (produced > item.size) damaged("a file unpacks to more than its recorded size")
            crc.update(b, off, n)
            return n
        }

        private fun readStored(b: ByteArray, off: Int, len: Int): Int {
            val left = item.compressedSize - fed
            if (left == 0L) return -1
            val n = readData(b, off, minOf(len.toLong(), left).toInt())
            fed += n
            return n
        }

        private fun readDeflated(inflater: Inflater, b: ByteArray, off: Int, len: Int): Int {
            while (true) {
                val n = try {
                    inflater.inflate(b, off, len)
                } catch (e: DataFormatException) {
                    damaged("a file's compressed data is corrupt")
                }
                if (n > 0) return n
                if (inflater.finished()) return -1
                if (inflater.needsDictionary() || !inflater.needsInput()) damaged("a file's compressed data is corrupt")
                run {
                    val left = item.compressedSize - fed
                    when {
                        left > 0 -> {
                            val k = readData(input, 0, minOf(input.size.toLong(), left).toInt())
                            fed += k
                            inflater.setInput(input, 0, k)
                        }
                        // zlib may want one byte past the end to finish; ZipFile gives it the same.
                        !dummyFed -> {
                            dummyFed = true
                            input[0] = 0
                            inflater.setInput(input, 0, 1)
                        }
                        else -> damaged("a file's compressed data ends early")
                    }
                }
            }
        }

        private fun readData(b: ByteArray, off: Int, len: Int): Int {
            raf.seek(item.dataOffset + fed)
            val n = raf.read(b, off, len)
            if (n <= 0) damaged("a file's data ends early")
            return n
        }

        private fun finish() {
            finished = true
            if (inflater != null && (fed != item.compressedSize || inflater.bytesRead != item.compressedSize)) {
                damaged("a file's compressed data has bytes after its end")
            }
            if (produced != item.size) damaged("a file unpacks to less than its recorded size")
            if (crc.value != item.crc) damaged("a file's CRC-32 doesn't match its data")
        }

        override fun close() {
            inflater?.end()
        }

        private fun damaged(why: String): Nothing =
            throw BackupException(BackupProblem.DAMAGED, "The file is incomplete or damaged: $why")
    }
}
