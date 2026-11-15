package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.TestBackups.assertRefused
import com.evergreen.trackora.backup.TestBackups.assertValid
import com.evergreen.trackora.backup.TestBackups.assertWriterRefuses
import com.evergreen.trackora.backup.TestBackups.entry
import com.evergreen.trackora.backup.TestBackups.included
import com.evergreen.trackora.backup.TestBackups.write
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.time.Instant
import java.util.Random
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Whatever the writer writes, the reader reads back unchanged, at every limit's edge. */
class RoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun theFixtureDataComesBackUnchanged() {
        val entries = ArrayList<BackupEntry>()
        val result = TestBackups.check(TestBackups.valid) { entries += it }
        val backup = (result as BackupCheck.Valid).backup
        assertEquals(FixtureData.entries, entries)
        assertEquals(FixtureData.settings, backup.settings)
        assertEquals(FixtureData.idHighWaterMark, backup.manifest.idHighWaterMark)
    }

    @Test
    fun aPhotoSeveralEntriesShareIsStoredOnce() {
        val bytes = write {
            writeSettings(FixtureData.emptySettings)
            writeEntry(entry(1, included("IMG_1.jpg")))
            writeEntry(entry(2, included("IMG_1.jpg")))
            writePhoto("IMG_1.jpg") { byteArrayOf(1, 2, 3).inputStream() }
            finish(2)
        }
        val backup = assertValid(bytes)
        assertEquals(1, backup.manifest.photoCount)
        assertEquals(listOf("photos/IMG_1.jpg"), backup.photos.map { it.path })
    }

    @Test
    fun aTimeWithNanosecondsIsKeptExactly() {
        val out = java.io.ByteArrayOutputStream()
        BackupWriter(out, Instant.parse("2026-11-12T12:00:00.123456789Z"), FixtureData.app, 2).use {
            it.writeSettings(FixtureData.emptySettings)
            it.finish(null)
        }
        assertEquals("2026-11-12T12:00:00.123456789Z", assertValid(out.toByteArray()).manifest.createdAt)
    }

    /** Random entries, text, photos and settings, a few hundred times over. */
    @Test
    fun randomBackupsComeBackUnchanged() {
        val random = Random(20261112)
        val pool = (0 until 0x80).map { it.toChar().toString() } +
            listOf("é", "é", "ی", "ک", "‌", "‏", " ", "﻿", "😀", "👩🏽‍🔧", "😀", "\"", "\\", "/")
        fun text(max: Int) = buildString { repeat(random.nextInt(max + 1)) { append(pool[random.nextInt(pool.size)]) } }
        fun maybe(max: Int) = if (random.nextInt(4) == 0) null else text(max)

        repeat(300) { round ->
            val photoNames = (0 until random.nextInt(4)).map { "IMG_${round}_$it.jpg" }
            val photoBytes = photoNames.associateWith { ByteArray(random.nextInt(3000)).also(random::nextBytes) }
            var id = random.nextLong() / 2
            val entries = (0 until random.nextInt(12)).map {
                id += 1 + random.nextInt(1000)
                val photo = when (random.nextInt(3)) {
                    0 -> null
                    1 -> if (photoNames.isEmpty()) null else included(photoNames[random.nextInt(photoNames.size)])
                    else -> BackupPhoto.missing(text(40), MissingPhoto.entries[random.nextInt(MissingPhoto.entries.size)])
                }
                BackupEntry(id, text(60), maybe(300), if (random.nextBoolean()) random.nextLong() else null,
                    BackupStatus.entries[random.nextInt(3)], "2026-0${1 + random.nextInt(9)}-1${random.nextInt(10)}",
                    maybe(20), maybe(20), maybe(20), photo)
            }
            val used = entries.mapNotNull { it.photo?.file }.toSortedSet()
            val settings = BackupSettings(text(30), text(30), text(30), random.nextBoolean(),
                BackupTheme.entries[random.nextInt(3)], BackupLanguage.entries[random.nextInt(3)])
            val mark = entries.lastOrNull()?.id?.let { maxOf(it, 0L) + random.nextInt(5) }

            val bytes = write {
                writeSettings(settings)
                entries.forEach(::writeEntry)
                used.forEach { writePhoto(it) { photoBytes.getValue(it).inputStream() } }
                finish(mark)
            }
            val read = ArrayList<BackupEntry>()
            val result = TestBackups.check(bytes) { read += it }
            assertTrue("round $round: $result", result is BackupCheck.Valid)
            assertEquals("round $round", entries, read)
            assertEquals("round $round", settings, (result as BackupCheck.Valid).backup.settings)

            val file = temp.newFile().apply { writeBytes(bytes) }
            val dir = temp.newFolder()
            for (name in used) {
                val extracted = BackupReader.extractPhoto(file, name, dir) as PhotoExtraction.Extracted
                assertArrayEquals(photoBytes.getValue(name), extracted.file.readBytes())
            }
        }
    }

    @Test
    fun anEntryLineAtTheLimitPassesAndOneByteMoreDoesNot() {
        val base = CanonicalJson.encode(entry(1, title = "")).toByteArray().size
        val limits = Limits.V1.copy(entryLineBytes = base + 100)
        val atLimit = write(limits) {
            writeSettings(FixtureData.emptySettings)
            writeEntry(entry(1, title = "x".repeat(100)))
            finish(1)
        }
        assertValid(atLimit, limits)
        assertWriterRefuses(BackupProblem.TOO_LARGE) {
            write(limits) { writeSettings(FixtureData.emptySettings); writeEntry(entry(1, title = "x".repeat(101))) }
        }
        val madeLarger = write { writeSettings(FixtureData.emptySettings); writeEntry(entry(1, title = "x".repeat(101))); finish(1) }
        assertRefused(BackupProblem.TOO_LARGE, madeLarger, limits)
    }

    @Test
    fun aPhotoAtTheLimitPassesAndOneByteMoreDoesNot() {
        val limits = Limits.V1.copy(photoBytes = 1000)
        fun backup(size: Int, limits: Limits) = write(limits) {
            writeSettings(FixtureData.emptySettings)
            writeEntry(entry(1, included("IMG_1.jpg")))
            writePhoto("IMG_1.jpg") { ByteArray(size).inputStream() }
            finish(1)
        }
        assertValid(backup(1000, limits), limits)
        assertWriterRefuses(BackupProblem.TOO_LARGE) { backup(1001, limits) }
        assertRefused(BackupProblem.TOO_LARGE, backup(1001, Limits.V1), limits)
    }

    @Test
    fun settingsAtTheLimitPassAndOneByteMoreDoNot() {
        val size = CanonicalJson.encode(FixtureData.settings).toByteArray().size
        val limits = Limits.V1.copy(settingsBytes = size)
        val bytes = write(limits) { writeSettings(FixtureData.settings); finish(null) }
        assertValid(bytes, limits)
        val smaller = Limits.V1.copy(settingsBytes = size - 1)
        assertWriterRefuses(BackupProblem.TOO_LARGE) { write(smaller) { writeSettings(FixtureData.settings) } }
        assertRefused(BackupProblem.TOO_LARGE, bytes, smaller)
    }

    @Test
    fun theFileCountAtTheLimitPassesAndOneMoreDoesNot() {
        fun backup(photos: Int, limits: Limits) = write(limits) {
            writeSettings(FixtureData.emptySettings)
            val names = (1..photos).map { "IMG_" + it.toString().padStart(3, '0') + ".jpg" }
            names.forEachIndexed { i, name -> writeEntry(entry(i + 1L, included(name))) }
            names.forEach { writePhoto(it) { byteArrayOf(1).inputStream() } }
            finish(photos.toLong())
        }
        val limits = Limits.V1.copy(files = 13) // settings, entries, 10 photos, manifest
        assertValid(backup(10, limits), limits)
        assertWriterRefuses(BackupProblem.TOO_LARGE) { backup(11, limits) }
        assertRefused(BackupProblem.TOO_LARGE, backup(11, Limits.V1), limits)
    }

    @Test
    fun theTotalSizeAtTheLimitPassesAndOneByteMoreDoesNot() {
        val bytes = TestBackups.valid
        val manifest = assertValid(bytes).manifest
        val manifestSize = CanonicalJson.encode(manifest).toByteArray().size
        val total = manifest.files.sumOf { it.size } + manifestSize
        val limits = Limits.V1.copy(totalBytes = total)
        assertValid(write(limits) { writeLikeTheFixture() }, limits)
        val smaller = Limits.V1.copy(totalBytes = total - 1)
        assertWriterRefuses(BackupProblem.TOO_LARGE) { write(smaller) { writeLikeTheFixture() } }
        assertRefused(BackupProblem.TOO_LARGE, bytes, smaller)
    }

    @Test
    fun theManifestAtTheLimitPassesAndOneByteMoreDoesNot() {
        val size = CanonicalJson.encode(assertValid(TestBackups.valid).manifest).toByteArray().size
        val limits = Limits.V1.copy(manifestBytes = size)
        assertValid(write(limits) { writeLikeTheFixture() }, limits)
        val smaller = Limits.V1.copy(manifestBytes = size - 1)
        assertWriterRefuses(BackupProblem.TOO_LARGE) { write(smaller) { writeLikeTheFixture() } }
        assertRefused(BackupProblem.TOO_LARGE, TestBackups.valid, smaller)
    }

    private fun BackupWriter.writeLikeTheFixture() {
        writeSettings(FixtureData.settings)
        FixtureData.entries.forEach(::writeEntry)
        FixtureData.photos.forEach { (name, bytes) -> writePhoto(name) { bytes.inputStream() } }
        finish(FixtureData.idHighWaterMark)
    }

    /** The real format 1 limit: a line of exactly 4 MiB is written and read; one byte more isn't written. */
    @Test
    fun aFourMebibyteEntryLineAtTheRealLimit() {
        val base = CanonicalJson.encode(entry(1, title = "")).toByteArray().size
        val title = "x".repeat(BackupFormat.MAX_ENTRY_LINE_BYTES - base)
        val bytes = write { writeSettings(FixtureData.emptySettings); writeEntry(entry(1, title = title)); finish(1) }
        val read = ArrayList<BackupEntry>()
        assertTrue(TestBackups.check(bytes) { read += it } is BackupCheck.Valid)
        assertEquals(title, read.single().title)
        assertWriterRefuses(BackupProblem.TOO_LARGE) {
            write { writeSettings(FixtureData.emptySettings); writeEntry(entry(1, title = title + "x")) }
        }
    }

    /** The real format 1 limit: a photo of exactly 256 MiB is written, read and unpacked; one byte more isn't written. */
    @Test
    fun a256MebibytePhotoAtTheRealLimit() {
        val file = temp.newFile("big.zip")
        file.outputStream().buffered().use { out ->
            BackupWriter(out, FixtureData.createdAt, FixtureData.app, 2).use {
                it.writeSettings(FixtureData.emptySettings)
                it.writeEntry(entry(1, included("IMG_big.jpg")))
                it.writePhoto("IMG_big.jpg") { Pattern(BackupFormat.MAX_PHOTO_BYTES) }
                it.finish(1)
            }
        }
        val result = BackupReader.verify(file)
        assertTrue("$result", result is BackupCheck.Valid)
        val extracted = BackupReader.extractPhoto(file, "IMG_big.jpg", temp.newFolder()) as PhotoExtraction.Extracted
        assertEquals(BackupFormat.MAX_PHOTO_BYTES, extracted.file.length())
        extracted.file.delete()
        file.delete()

        assertWriterRefuses(BackupProblem.TOO_LARGE) {
            BackupWriter(NullOutput, FixtureData.createdAt, FixtureData.app, 2).use {
                it.writeSettings(FixtureData.emptySettings)
                it.writeEntry(entry(1, included("IMG_big.jpg")))
                it.writePhoto("IMG_big.jpg") { Pattern(BackupFormat.MAX_PHOTO_BYTES + 1) }
            }
        }
    }

    @Test
    fun otherZipToolsCanOpenWhatTheWriterWrites() {
        val file = temp.newFile().apply { writeBytes(TestBackups.valid) }
        ZipFile(file).use { zip ->
            assertEquals(
                listOf("settings.json", "entries.jsonl", "photos/${FixtureData.CAMERA_PHOTO}", "photos/${FixtureData.GALLERY_PHOTO}", "manifest.json"),
                zip.entries().toList().map { it.name },
            )
            assertArrayEquals(FixtureData.photos.getValue(FixtureData.CAMERA_PHOTO), zip.getInputStream(zip.getEntry("photos/${FixtureData.CAMERA_PHOTO}")).readBytes())
        }
        ZipInputStream(TestBackups.valid.inputStream()).use { zip ->
            var count = 0
            while (zip.nextEntry != null) { zip.readBytes(); count++ }
            assertEquals(5, count)
        }
    }

    /** [size] bytes of a repeating pattern, without holding them in memory. */
    private class Pattern(private val size: Long) : InputStream() {
        private var position = 0L
        override fun read(): Int = if (position < size) (position++ % 251).toInt() else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= size) return -1
            val n = minOf(len.toLong(), size - position).toInt()
            for (i in 0 until n) b[off + i] = ((position + i) % 251).toByte()
            position += n
            return n
        }
    }

    private object NullOutput : java.io.OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}
