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
    val crc: Long,
    val compressedSize: Long,
    val size: Long,
    val dataOffset: Long,
)

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
    private const val FLAG_STRONG_ENCRYPTION = 0x0040

    /** Deflate options (bits 1, 2), data descriptor (3), UTF-8 names (11): what ZipOutputStream sets. */
    private const val ALLOWED_FLAGS = 0x0002 or 0x0004 or FLAG_DESCRIPTOR or 0x0800

    /** 2.0: deflate. ZIP64 needs 4.5, and encryption schemes more. */
    private const val MAX_VERSION_NEEDED = 20
    private const val ZIP64_EXTRA_ID = 0x0001
    private const val MAX_CENTRAL_DIRECTORY = 64 * 1024 * 1024

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
        if (centralSize > MAX_CENTRAL_DIRECTORY) throw BackupException(BackupProblem.TOO_LARGE, "The ZIP's central directory is too large")

        val central = readAt(raf, centralOffset, centralSize.toInt(), length) ?: broken("its central directory can't be read")
        val records = ArrayList<Record>(total)
        var p = 0
        for (i in 1..total) {
            if (p + CENTRAL_SIZE > central.size || u32(central, p) != CENTRAL_SIGNATURE) broken("central directory record $i is damaged")
            val versionNeeded = u16(central, p + 6)
            val flags = u16(central, p + 8)
            val method = u16(central, p + 10)
            val crc = u32(central, p + 16)
            val compressedSize = u32(central, p + 20)
            val size = u32(central, p + 24)
            val nameLength = u16(central, p + 28)
            val extraLength = u16(central, p + 30)
            val commentLength = u16(central, p + 32)
            val diskStart = u16(central, p + 34)
            val localOffset = u32(central, p + 42)
            val next = p + CENTRAL_SIZE + nameLength + extraLength + commentLength
            if (next > central.size) broken("central directory record $i runs past its end")

            if (flags and (FLAG_ENCRYPTED or FLAG_STRONG_ENCRYPTION) != 0) unsupported("encryption")
            if (flags and ALLOWED_FLAGS.inv() != 0) unsupported("flags 0x${Integer.toHexString(flags)}")
            if (method != STORED && method != DEFLATED) unsupported("compression method $method")
            if (method == STORED && flags and FLAG_DESCRIPTOR != 0) unsupported("a data descriptor on a stored file")
            if (versionNeeded > MAX_VERSION_NEEDED) unsupported("features of ZIP version ${versionNeeded / 10}.${versionNeeded % 10}")
            if (compressedSize == 0xFFFFFFFFL || size == 0xFFFFFFFFL || localOffset == 0xFFFFFFFFL || diskStart == 0xFFFF) unsupported("ZIP64")
            if (diskStart != 0) unsupported("several disks")
            if (commentLength != 0) unsupported("file comments")
            if (method == STORED && compressedSize != size) broken("a stored file's two sizes differ")
            if (nameLength == 0) broken("a file has no name")
            checkExtra(central, p + CENTRAL_SIZE + nameLength, extraLength, ::broken, ::unsupported)

            records += Record(
                nameBytes = central.copyOfRange(p + CENTRAL_SIZE, p + CENTRAL_SIZE + nameLength),
                flags = flags,
                method = method,
                crc = crc,
                compressedSize = compressedSize,
                size = size,
                localOffset = localOffset,
            )
            p = next
        }
        if (p != central.size) broken("its central directory has bytes after the last record")

        var expected = 0L
        val items = records.map { r ->
            if (r.localOffset != expected) broken("its files don't lie back to back")
            if (r.localOffset + LOCAL_SIZE > centralOffset) broken("a file runs into the central directory")
            val header = readAt(raf, r.localOffset, LOCAL_SIZE, length) ?: broken("a file header can't be read")
            if (u32(header, 0) != LOCAL_SIGNATURE) broken("a file header is damaged")
            val versionNeeded = u16(header, 4)
            val flags = u16(header, 6)
            val method = u16(header, 8)
            val crc = u32(header, 14)
            val compressedSize = u32(header, 18)
            val size = u32(header, 22)
            val nameLength = u16(header, 26)
            val extraLength = u16(header, 28)
            if (versionNeeded > MAX_VERSION_NEEDED) unsupported("features of ZIP version ${versionNeeded / 10}.${versionNeeded % 10}")
            if (flags != r.flags || method != r.method) broken("a file header disagrees with the central directory")
            val descriptor = r.flags and FLAG_DESCRIPTOR != 0
            fun agrees(local: Long, central: Long) = local == central || (descriptor && local == 0L)
            if (!agrees(crc, r.crc) || !agrees(compressedSize, r.compressedSize) || !agrees(size, r.size)) {
                broken("a file header disagrees with the central directory")
            }
            val variableStart = r.localOffset + LOCAL_SIZE
            if (variableStart + nameLength + extraLength > centralOffset) broken("a file runs into the central directory")
            val variable = readAt(raf, variableStart, nameLength + extraLength, length) ?: broken("a file header can't be read")
            if (!variable.copyOfRange(0, nameLength).contentEquals(r.nameBytes)) broken("a file's two names differ")
            checkExtra(variable, nameLength, extraLength, ::broken, ::unsupported)

            val dataOffset = variableStart + nameLength + extraLength
            var next = dataOffset + r.compressedSize
            if (descriptor) {
                if (next + DESCRIPTOR_SIZE > centralOffset) broken("a file runs into the central directory")
                val d = readAt(raf, next, DESCRIPTOR_SIZE, length) ?: broken("a data descriptor can't be read")
                if (u32(d, 0) != DESCRIPTOR_SIGNATURE || u32(d, 4) != r.crc || u32(d, 8) != r.compressedSize || u32(d, 12) != r.size) {
                    broken("a data descriptor disagrees with the central directory")
                }
                next += DESCRIPTOR_SIZE
            }
            if (next > centralOffset) broken("a file runs into the central directory")
            expected = next
            ZipItem(
                name = String(r.nameBytes, Charsets.ISO_8859_1),
                method = r.method,
                crc = r.crc,
                compressedSize = r.compressedSize,
                size = r.size,
                dataOffset = dataOffset,
            )
        }
        if (expected != centralOffset) broken("there are bytes between the last file and the central directory")
        return items
    }

    /** Unpacks [item], checking it unpacks to exactly its recorded size and CRC-32. */
    fun open(raf: RandomAccessFile, item: ZipItem): InputStream = ItemInput(raf, item)

    private class Record(
        val nameBytes: ByteArray,
        val flags: Int,
        val method: Int,
        val crc: Long,
        val compressedSize: Long,
        val size: Long,
        val localOffset: Long,
    )

    private inline fun checkExtra(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        broken: (String) -> Nothing,
        unsupported: (String) -> Nothing,
    ) {
        var p = offset
        val end = offset + length
        while (p < end) {
            if (end - p < 4) broken("a file's extra field is malformed")
            val id = u16(bytes, p)
            val size = u16(bytes, p + 2)
            if (p + 4 + size > end) broken("a file's extra field is malformed")
            if (id == ZIP64_EXTRA_ID) unsupported("ZIP64")
            p += 4 + size
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
