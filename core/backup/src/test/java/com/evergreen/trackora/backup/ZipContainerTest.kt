package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.BackupProblem.DAMAGED
import com.evergreen.trackora.backup.BackupProblem.NOT_A_ZIP
import com.evergreen.trackora.backup.BackupProblem.TOO_LARGE
import com.evergreen.trackora.backup.BackupProblem.UNEXPECTED_FILE
import com.evergreen.trackora.backup.BackupProblem.UNSUPPORTED_ZIP
import com.evergreen.trackora.backup.TestBackups.assertRefused
import com.evergreen.trackora.backup.TestBackups.assertValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ZIP container: only the single unambiguous layout the writer makes. */
class ZipContainerTest {

    private val files get() = Craft().files
    private val camera = BackupFormat.PHOTOS_DIR + FixtureData.CAMERA_PHOTO

    @Test
    fun theWritersOwnLayoutRebuiltByHandIsValid() {
        assertValid(RawZip.of(files))
    }

    @Test
    fun aFileThatIsNotAZipIsNotAZip() {
        assertRefused(NOT_A_ZIP, ByteArray(0))
        assertRefused(NOT_A_ZIP, "short".toByteArray())
        assertRefused(NOT_A_ZIP, FixtureData.pseudoRandom(7, 5000))
        assertRefused(NOT_A_ZIP, "%PDF-1.7\n".toByteArray() + ByteArray(100))
    }

    @Test
    fun aZipCutShortAnywhereIsDamaged() {
        val valid = TestBackups.valid
        for (length in listOf(2, 4, 30, 100, valid.size / 2, valid.size - 23, valid.size - 22, valid.size - 1)) {
            assertRefused(DAMAGED, valid.copyOf(length))
        }
    }

    @Test
    fun bytesBeforeOrAfterTheArchiveAreRefused() {
        assertRefused(DAMAGED, TestBackups.valid + byteArrayOf(0))
        assertRefused(DAMAGED, TestBackups.valid + "PK\u0005\u0006".toByteArray())
        assertRefused(NOT_A_ZIP, "MZ".toByteArray() + TestBackups.valid)
        assertRefused(DAMAGED, "PK".toByteArray() + TestBackups.valid)
    }

    @Test
    fun aZipCommentIsRefused() {
        val valid = TestBackups.valid
        val withComment = valid.copyOf().also { it[it.size - 2] = 3 } + "abc".toByteArray()
        assertRefused(DAMAGED, withComment)
    }

    @Test
    fun gapsAndOverlapsBetweenFilesAreRefused() {
        assertRefused(DAMAGED, RawZip.of(files) { items[1].gapAfter = 1 })
        assertRefused(DAMAGED, RawZip.of(files) { items[0].gapAfter = 7 })
        assertRefused(DAMAGED, RawZip.of(files) { items[2].localOffset = 0 })
        assertRefused(DAMAGED, RawZip.of(files) { items[3].localOffset = 1 })
    }

    @Test
    fun aLocalHeaderDisagreeingWithTheCentralDirectoryIsRefused() {
        assertRefused(DAMAGED, RawZip.of(files) { items[0].localName = "settings.jsoN".toByteArray() })
        assertRefused(DAMAGED, RawZip.of(files) { items[0].localFlags = 0x0008 })
        assertRefused(DAMAGED, RawZip.of(files) { items[1].descriptorCrc = 1 })
    }

    @Test
    fun aWrongCrcOrSizeIsDamaged() {
        assertRefused(DAMAGED, RawZip.of(files) { items[0].crc = 12345 })
        // Sizes the manifest agrees with, so it is the unpacking that finds them wrong.
        for (delta in listOf(1L, -1L)) {
            val craft = Craft().manifest { m -> m.copy(files = m.files.map { if (it.path == camera) it.copy(size = it.size + delta) else it }) }
            assertRefused(DAMAGED, craft.build { items.first { it.name == camera }.let { it.size = it.data.size + delta } })
        }
    }

    @Test
    fun compressedDataWithBytesAfterItsEndIsDamaged() {
        assertRefused(DAMAGED, RawZip.of(files) { items[0].compressed = RawZip.deflate(items[0].data) + byteArrayOf(0, 0, 0) })
    }

    @Test
    fun corruptCompressedDataIsDamaged() {
        assertRefused(DAMAGED, RawZip.of(files) { items[1].compressed = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) })
        assertRefused(DAMAGED, RawZip.of(files) { items[1].compressed = RawZip.deflate(items[1].data).let { it.copyOf(it.size - 4) } })
    }

    @Test
    fun storedFilesAreAccepted() {
        assertValid(RawZip.of(files) { items.forEach { it.method = 0 } })
    }

    @Test
    fun zipFeaturesTheFormatExcludesAreUnsupported() {
        val cases = listOf<RawZip.() -> Unit>(
            { encryptFlag = true },
            { items[0].flags = 0x0808 or 0x0040 },
            { items[0].flags = 0x0808 or 0x2000 },
            { items[0].flags = 0x0808 or 0x0010 },
            { items[0].method = 12 },
            { items[0].method = 0; items[0].flags = 0x0808 },
            { items[0].versionNeeded = 45 },
            { items[0].extra = byteArrayOf(0x01, 0x00, 0x08, 0x00) + ByteArray(8) },
            { items[0].comment = "note".toByteArray() },
            { items[0].flags = 0x0800 or 0x0001 },
            { total = 0xFFFF },
        )
        cases.forEachIndexed { i, case -> assertRefused(UNSUPPORTED_ZIP, RawZip.of(files, case)).also { assertTrue("case $i", it.isNotEmpty()) } }
    }

    /**
     * Other tools act on extra fields: a Unicode path field renames a file
     * (libarchive lists it under the new name), so none is accepted, in either
     * header, whatever its kind.
     */
    @Test
    fun everyExtraFieldIsRefused() {
        val unicodePath = run {
            val alias = "../../evil.jpg".toByteArray()
            val crc = RawZip.crc32(camera.toByteArray()).let { byteArrayOf(it.toByte(), (it shr 8).toByte(), (it shr 16).toByte(), (it shr 24).toByte()) }
            byteArrayOf(0x75, 0x70, (5 + alias.size).toByte(), 0x00, 0x01) + crc + alias
        }
        val extras = mapOf(
            "Unicode path" to unicodePath,
            "extended timestamp" to byteArrayOf(0x55, 0x54, 0x05, 0x00, 1, 0, 0, 0, 0),
            "ZIP64" to byteArrayOf(0x01, 0x00, 0x08, 0x00) + ByteArray(8),
            "empty, unknown" to byteArrayOf(0x34, 0x12, 0x00, 0x00),
            "malformed" to byteArrayOf(0x55, 0x54, 0x09, 0x00, 1),
        )
        for ((kind, extra) in extras) {
            val photo = files.indexOfFirst { it.first == camera }
            val inBoth = assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[photo].extra = extra })
            assertTrue("$kind: $inBoth", "extra fields" in inBoth)
            val localOnly = assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[photo].localExtra = extra })
            assertTrue("$kind: $localOnly", "extra fields" in localOnly)
        }
    }

    @Test
    fun aSymbolicLinkOrAnyFileAttributeIsRefused() {
        val photo = files.indexOfFirst { it.first == camera }
        for (attributes in listOf(0xA1FFL shl 16, 0x81A4L shl 16, 0x20L, 0x10L)) {
            val detail = assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[photo].externalAttributes = attributes })
            assertTrue(detail, "symbolic link" in detail)
        }
        assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[photo].internalAttributes = 1 })
    }

    @Test
    fun anArchiveMadeOnAnotherSystemIsRefused() {
        val photo = files.indexOfFirst { it.first == camera }
        // Unix as the host, as Python's zipfile and Info-ZIP write it on Linux and macOS.
        assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[photo].madeBy = 0x0300 or 10 })
        assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[0].madeBy = 0x0300 or 20 })
        // A "version made by" other than the one ZipOutputStream writes.
        assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[0].madeBy = 45 })
        assertRefused(UNSUPPORTED_ZIP, RawZip.of(files) { items[0].madeBy = 10 })
    }

    @Test
    fun flagsZipOutputStreamNeverWritesAreRefused() {
        val photo = files.indexOfFirst { it.first == camera }
        val cases = listOf<RawZip.() -> Unit>(
            { items[0].flags = 0x0808 or 0x0002 },
            { items[0].flags = 0x0808 or 0x0004 },
            { items[0].flags = 0x0008 },
            { items[photo].method = 0; items[photo].flags = 0x0000 },
            // Deflated with the sizes up front instead of a data descriptor.
            { items[0].descriptor = false; items[0].flags = 0x0800 },
        )
        cases.forEachIndexed { i, case -> assertRefused(UNSUPPORTED_ZIP, RawZip.of(files, case)).also { assertTrue("case $i: $it", "flags" in it) } }
    }

    @Test
    fun aLocalHeaderMustMatchExactlyWhatZipOutputStreamWrites() {
        // The data descriptor carries a deflated file's CRC-32 and sizes; its local header has zeros.
        assertRefused(DAMAGED, RawZip.of(files) { items[0].localSizes = true })
        assertRefused(DAMAGED, RawZip.of(files) { items[0].localVersion = 10 })
        assertRefused(DAMAGED, RawZip.of(files) { items[0].localVersion = 0x0314 })
    }

    @Test
    fun aNameLongerThanAnyBackupUsesIsRefusedBeforeItIsRead() {
        val longest = BackupFormat.PHOTOS_DIR + "A".repeat(BackupFormat.MAX_PHOTO_NAME_LENGTH)
        assertEquals(135, longest.length)
        // 135 bytes passes the container (and is then an unexpected file, as no entry uses it).
        assertRefused(UNEXPECTED_FILE, Craft().put(longest, "x").build())
        val detail = assertRefused(UNEXPECTED_FILE, Craft().put(longest + "A", "x").build())
        assertTrue(detail, "136 bytes" in detail)
        // A name so long the central directory outgrows its bound is refused by that bound, unread.
        val huge = assertRefused(UNSUPPORTED_ZIP, Craft().put("photos/" + "A".repeat(65_000), "x").build())
        assertTrue(huge, "larger than 6 file names can need" in huge)
    }

    @Test
    fun aCentralDirectoryLargerThanItsNamesCanNeedIsRefusedBeforeItIsRead() {
        // The end record claims a 64 MiB central directory for one file; the file is sparse padding.
        val claimed = 64L * 1024 * 1024
        val file = java.io.File.createTempFile("m2-bound", ".zip")
        try {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                raf.write("PK".toByteArray())
                raf.setLength(claimed + 22)
                raf.seek(claimed)
                raf.write(endRecord(total = 1, centralSize = claimed, centralOffset = 0))
            }
            val result = BackupReader.verify(file)
            assertTrue("$result", result is BackupCheck.Invalid && result.problem == UNSUPPORTED_ZIP)
            assertTrue("$result", "larger than 1 file names can need" in (result as BackupCheck.Invalid).detail)
        } finally {
            file.delete()
        }
    }

    private fun endRecord(total: Int, centralSize: Long, centralOffset: Long): ByteArray {
        val b = java.nio.ByteBuffer.allocate(22).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x06054b50).putShort(0).putShort(0).putShort(total.toShort()).putShort(total.toShort())
        b.putInt(centralSize.toInt()).putInt(centralOffset.toInt()).putShort(0)
        return b.array()
    }

    @Test
    fun anEndRecordCountingWrongIsDamaged() {
        assertRefused(DAMAGED, RawZip.of(files) { total = items.size - 1 })
        assertRefused(DAMAGED, RawZip.of(files) { total = items.size + 1 })
    }

    @Test
    fun moreFilesThanTheLimitIsTooLarge() {
        assertRefused(TOO_LARGE, TestBackups.valid, Limits.V1.copy(files = 4))
    }
}
