package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.BackupProblem.DAMAGED
import com.evergreen.trackora.backup.BackupProblem.NOT_A_ZIP
import com.evergreen.trackora.backup.BackupProblem.TOO_LARGE
import com.evergreen.trackora.backup.BackupProblem.UNSUPPORTED_ZIP
import com.evergreen.trackora.backup.TestBackups.assertRefused
import com.evergreen.trackora.backup.TestBackups.assertValid
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
            { total = 0xFFFF },
        )
        cases.forEachIndexed { i, case -> assertRefused(UNSUPPORTED_ZIP, RawZip.of(files, case)).also { assertTrue("case $i", it.isNotEmpty()) } }
    }

    @Test
    fun harmlessExtraFieldsAreAllowedButMalformedOnesAreNot() {
        assertValid(RawZip.of(files) { items[0].extra = byteArrayOf(0x55, 0x54, 0x05, 0x00, 1, 0, 0, 0, 0) })
        assertRefused(DAMAGED, RawZip.of(files) { items[0].extra = byteArrayOf(0x55, 0x54, 0x09, 0x00, 1) })
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
