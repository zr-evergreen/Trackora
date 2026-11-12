package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.BackupProblem.DUPLICATE_FILE
import com.evergreen.trackora.backup.BackupProblem.INCONSISTENT
import com.evergreen.trackora.backup.BackupProblem.INVALID_ENTRY
import com.evergreen.trackora.backup.BackupProblem.INVALID_MANIFEST
import com.evergreen.trackora.backup.BackupProblem.INVALID_SETTINGS
import com.evergreen.trackora.backup.BackupProblem.MISSING_FILE
import com.evergreen.trackora.backup.BackupProblem.NOT_A_TRACKORA_BACKUP
import com.evergreen.trackora.backup.BackupProblem.UNSAFE_NAME
import com.evergreen.trackora.backup.TestBackups.assertRefused
import com.evergreen.trackora.backup.TestBackups.assertValid
import com.evergreen.trackora.backup.TestBackups.assertWriterRefuses
import com.evergreen.trackora.backup.TestBackups.entry
import com.evergreen.trackora.backup.TestBackups.included
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

/** The writer refuses whatever the reader would refuse, so it can't make a backup that won't restore. */
class BackupWriterTest {

    private fun writer(out: ByteArrayOutputStream = ByteArrayOutputStream(), appVersion: String = "1.0", databaseVersion: Int = 2) =
        BackupWriter(out, FixtureData.createdAt, FixtureData.app.copy(versionName = appVersion), databaseVersion)

    private fun started() = writer().apply { writeSettings(FixtureData.emptySettings) }

    @Test
    fun stepsOutOfOrderAreAMistake() {
        expectState { writer().writeEntry(entry(1)) }
        expectState { writer().finish(null) }
        expectState { started().writeSettings(FixtureData.emptySettings) }
        expectState { started().apply { finish(null) }.writeEntry(entry(1)) }
        expectState {
            started().apply {
                writeEntry(entry(1, included("IMG_1.jpg")))
                writePhoto("IMG_1.jpg") { byteArrayOf(1).inputStream() }
            }.writeEntry(entry(2))
        }
    }

    @Test
    fun afterAFailedStepNothingMoreIsWritten() {
        val w = started()
        assertWriterRefuses(INVALID_ENTRY) { w.writeEntry(entry(1, date = "not a date")) }
        expectState { w.writeEntry(entry(1)) }
        expectState { w.finish(null) }
    }

    @Test
    fun invalidValuesAreRefused() {
        assertWriterRefuses(INVALID_SETTINGS) { writer().writeSettings(FixtureData.emptySettings.copy(customField1Name = "\uD800")) }
        listOf(
            entry(1, date = "2023-02-29"),
            entry(1, title = "\uDC00"),
            entry(1).copy(description = "a\uD800"),
            entry(1).copy(customField3 = "\uD800\uD800"),
            entry(1, BackupPhoto.missing("\uD800", MissingPhoto.NOT_FOUND)),
            entry(1, BackupPhoto("IMG_1.jpg", "x", MissingPhoto.NOT_FOUND)),
            entry(1, BackupPhoto(null, "x", null)),
            entry(1, BackupPhoto("../IMG_1.jpg", "x", null)),
        ).forEach { e -> assertWriterRefuses(INVALID_ENTRY) { started().writeEntry(e) } }
    }

    @Test
    fun idsMustAscendEachOnce() {
        assertWriterRefuses(INVALID_ENTRY) { started().apply { writeEntry(entry(2)) }.writeEntry(entry(2)) }
        assertWriterRefuses(INVALID_ENTRY) { started().apply { writeEntry(entry(2)) }.writeEntry(entry(1)) }
    }

    @Test
    fun photoNamesDifferingOnlyInCaseAreRefused() {
        assertWriterRefuses(DUPLICATE_FILE) {
            started().apply { writeEntry(entry(1, included("IMG_1.jpg"))) }.writeEntry(entry(2, included("img_1.JPG")))
        }
    }

    @Test
    fun photosMustBeUsedSafeAndInOrder() {
        fun withPhotos() = started().apply {
            writeEntry(entry(1, included("IMG_1.jpg")))
            writeEntry(entry(2, included("IMG_2.jpg")))
        }
        assertWriterRefuses(UNSAFE_NAME) { withPhotos().writePhoto("../IMG_1.jpg") { byteArrayOf(1).inputStream() } }
        assertWriterRefuses(INCONSISTENT) { withPhotos().writePhoto("IMG_3.jpg") { byteArrayOf(1).inputStream() } }
        assertWriterRefuses(INCONSISTENT) { withPhotos().writePhoto("img_1.jpg") { byteArrayOf(1).inputStream() } }
        assertWriterRefuses(INCONSISTENT) {
            withPhotos().apply { writePhoto("IMG_2.jpg") { byteArrayOf(1).inputStream() } }.writePhoto("IMG_1.jpg") { byteArrayOf(1).inputStream() }
        }
        assertWriterRefuses(DUPLICATE_FILE) {
            withPhotos().apply { writePhoto("IMG_1.jpg") { byteArrayOf(1).inputStream() } }.writePhoto("IMG_1.jpg") { byteArrayOf(1).inputStream() }
        }
    }

    @Test
    fun aPhotoThatChangesBetweenItsTwoReadsIsRefused() {
        val versions = listOf(
            byteArrayOf(1, 2, 3) to byteArrayOf(1, 2, 3, 4),
            byteArrayOf(1, 2, 3) to byteArrayOf(1, 2),
            byteArrayOf(1, 2, 3) to byteArrayOf(1, 2, 4),
        )
        for ((first, second) in versions) {
            var reads = 0
            assertWriterRefuses(INCONSISTENT) {
                started().apply { writeEntry(entry(1, included("IMG_1.jpg"))) }
                    .writePhoto("IMG_1.jpg") { (if (reads++ == 0) first else second).inputStream() }
            }
        }
    }

    @Test
    fun everyStreamThePhotoWriterOpensIsClosed() {
        val opened = ArrayList<Closing>()
        TestBackups.write {
            writeSettings(FixtureData.emptySettings)
            writeEntry(entry(1, included("IMG_1.jpg")))
            writePhoto("IMG_1.jpg") { Closing(byteArrayOf(1, 2, 3)).also { opened += it } }
            finish(1)
        }
        assertEquals(2, opened.size)
        assertEquals(listOf(true, true), opened.map { it.closed })
    }

    /**
     * Photos stored, JSON deflated, no other mix: changing ZipOutputStream's
     * level between files corrupts the archive on Android 12 and 13.
     */
    @Test
    fun photosAreStoredAndTheJsonDeflated() {
        val file = java.io.File.createTempFile("backup", ".zip")
        try {
            file.writeBytes(TestBackups.valid)
            val items = java.io.RandomAccessFile(file, "r").use { ZipLayout.read(it, Limits.V1) }
            assertEquals(
                listOf(
                    BackupFormat.SETTINGS to ZipLayout.DEFLATED,
                    BackupFormat.ENTRIES to ZipLayout.DEFLATED,
                    BackupFormat.PHOTOS_DIR + FixtureData.CAMERA_PHOTO to ZipLayout.STORED,
                    BackupFormat.PHOTOS_DIR + FixtureData.GALLERY_PHOTO to ZipLayout.STORED,
                    BackupFormat.MANIFEST to ZipLayout.DEFLATED,
                ),
                items.map { it.name to it.method },
            )
        } finally {
            file.delete()
        }
    }

    private class Closing(bytes: ByteArray) : java.io.ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }

    @Test
    fun everyIncludedPhotoMustBeWritten() {
        val detail = assertWriterRefuses(MISSING_FILE) {
            started().apply { writeEntry(entry(1, included("IMG_1.jpg"))) }.finish(1)
        }
        assertEquals("An entry includes photo \"IMG_1.jpg\", but it wasn't written", detail)
    }

    @Test
    fun theIdCounterMustBeAsSqliteKeepsIt() {
        assertWriterRefuses(INCONSISTENT) { started().apply { writeEntry(entry(5)) }.finish(4) }
        assertWriterRefuses(INCONSISTENT) { started().finish(-1) }
        assertValid(TestBackups.write { writeSettings(FixtureData.emptySettings); writeEntry(entry(-5)); finish(0) })
        assertValid(TestBackups.write { writeSettings(FixtureData.emptySettings); writeEntry(entry(5)); finish(5) })
        assertValid(TestBackups.write { writeSettings(FixtureData.emptySettings); writeEntry(entry(5)); finish(null) })
    }

    @Test
    fun manifestValuesAreChecked() {
        assertWriterRefuses(INVALID_MANIFEST) { writer(databaseVersion = 0).apply { writeSettings(FixtureData.emptySettings) }.finish(null) }
        assertWriterRefuses(INVALID_MANIFEST) { writer(appVersion = "\uD800").apply { writeSettings(FixtureData.emptySettings) }.finish(null) }
    }

    @Test
    fun anUnfinishedBackupIsRefusedByEveryReader() {
        val out = ByteArrayOutputStream()
        writer(out).use {
            it.writeSettings(FixtureData.settings)
            it.writeEntry(entry(1))
        }
        assertRefused(NOT_A_TRACKORA_BACKUP, out.toByteArray())
    }

    @Test
    fun aBackupAbandonedAfterAFailureIsRefusedByEveryReader() {
        val out = ByteArrayOutputStream()
        writer(out).use {
            it.writeSettings(FixtureData.settings)
            it.writeEntry(entry(1, included("IMG_1.jpg")))
            assertWriterRefuses(UNSAFE_NAME) { it.writePhoto("../IMG_1.jpg") { byteArrayOf(1).inputStream() } }
        }
        assertRefused(NOT_A_TRACKORA_BACKUP, out.toByteArray())
    }

    @Test
    fun finishReturnsTheManifestItWrote() {
        val out = ByteArrayOutputStream()
        val manifest = FixtureData.writeValid(out)
        assertEquals(manifest, assertValid(out.toByteArray()).manifest)
    }

    private fun expectState(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalStateException")
        } catch (expected: IllegalStateException) {
        }
    }
}
