package com.evergreen.trackora.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.util.Random
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Backups written on Android, with Android's ZipOutputStream and Deflater,
 * read back by the reader with Android's Inflater. Synthetic data only, in
 * the test APK's own cache folder.
 */
@RunWith(AndroidJUnit4::class)
class WriterOnDeviceTest {

    private val scratch = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "writer-on-device")
        .apply { deleteRecursively(); mkdirs() }

    @After
    fun cleanUp() {
        scratch.deleteRecursively()
    }

    @Test
    fun theFixtureDataWrittenHereReadsBackUnchanged() {
        val file = File(scratch, "valid.zip")
        file.outputStream().use { FixtureData.writeValid(it) }
        val entries = ArrayList<BackupEntry>()
        val result = BackupReader.readEntries(file) { entries += it }
        assertTrue("$result", result is BackupCheck.Valid)
        assertEquals(FixtureData.entries, entries)
        assertEquals(FixtureData.settings, (result as BackupCheck.Valid).backup.settings)

        val photos = File(scratch, "photos").apply { mkdirs() }
        for ((name, bytes) in FixtureData.photos) {
            val extracted = BackupReader.extractPhoto(file, name, photos) as PhotoExtraction.Extracted
            assertArrayEquals(bytes, extracted.file.readBytes())
        }
    }

    /** Android's own ZIP readers open what the writer writes, so other tools can too. */
    @Test
    fun androidsZipReadersOpenWhatTheWriterWrites() {
        val file = File(scratch, "valid.zip")
        file.outputStream().use { FixtureData.writeValid(it) }
        val expected = listOf(
            BackupFormat.SETTINGS,
            BackupFormat.ENTRIES,
            BackupFormat.PHOTOS_DIR + FixtureData.CAMERA_PHOTO,
            BackupFormat.PHOTOS_DIR + FixtureData.GALLERY_PHOTO,
            BackupFormat.MANIFEST,
        )
        ZipFile(file).use { zip ->
            assertEquals(expected, zip.entries().toList().map { it.name })
            val camera = zip.getEntry(BackupFormat.PHOTOS_DIR + FixtureData.CAMERA_PHOTO)
            assertArrayEquals(FixtureData.photos.getValue(FixtureData.CAMERA_PHOTO), zip.getInputStream(camera).use { it.readBytes() })
        }
        val names = ArrayList<String>()
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                zip.readBytes()
                names += entry.name
            }
        }
        assertEquals(expected, names)
    }

    @Test
    fun randomBackupsWrittenHereReadBackUnchanged() {
        val random = Random(24)
        val pool = (0 until 0x80).map { it.toChar().toString() } + listOf("é", "é", "ی", "‌", "‏", "😀", "👩🏽‍🔧")
        fun text(max: Int) = buildString { repeat(random.nextInt(max + 1)) { append(pool[random.nextInt(pool.size)]) } }
        repeat(40) { round ->
            val names = (0 until random.nextInt(3)).map { "IMG_${round}_$it.jpg" }
            val bytes = names.associateWith { ByteArray(random.nextInt(200_000)).also(random::nextBytes) }
            val entries = (1..random.nextInt(20)).map { i ->
                val photo = if (names.isNotEmpty() && random.nextBoolean()) {
                    names[random.nextInt(names.size)].let { BackupPhoto.included(it, "content://com.evergreen.trackora.fileprovider/external_files/$it") }
                } else {
                    null
                }
                BackupEntry(i * 10L, text(80), text(400), random.nextLong(), BackupStatus.entries[random.nextInt(3)],
                    "2026-11-${10 + random.nextInt(19)}", text(10), null, text(10), photo)
            }
            val used = entries.mapNotNull { it.photo?.file }.toSortedSet()
            val file = File(scratch, "random-$round.zip")
            file.outputStream().use { out ->
                BackupWriter(out, FixtureData.createdAt, FixtureData.app, 2).use { writer ->
                    writer.writeSettings(FixtureData.settings)
                    entries.forEach(writer::writeEntry)
                    used.forEach { writer.writePhoto(it) { bytes.getValue(it).inputStream() } }
                    writer.finish(entries.lastOrNull()?.id)
                }
            }
            val read = ArrayList<BackupEntry>()
            val result = BackupReader.readEntries(file) { read += it }
            assertTrue("round $round: $result", result is BackupCheck.Valid)
            assertEquals("round $round", entries, read)
            file.delete()
        }
    }

    /** A photo large enough to stream through many buffers on the way in and out. */
    @Test
    fun aLargePhotoStreamsThroughUnchanged() {
        val size = 64L * 1024 * 1024
        val file = File(scratch, "large.zip")
        file.outputStream().buffered().use { out ->
            BackupWriter(out, FixtureData.createdAt, FixtureData.app, 2).use { writer ->
                writer.writeSettings(FixtureData.emptySettings)
                writer.writeEntry(BackupEntry(1, "Large photo", null, null, BackupStatus.COMPLETED, "2026-11-12", null, null, null,
                    BackupPhoto.included("IMG_large.jpg", "content://com.evergreen.trackora.fileprovider/external_files/IMG_large.jpg")))
                writer.writePhoto("IMG_large.jpg") { Pattern(size) }
                writer.finish(1)
            }
        }
        assertTrue(BackupReader.verify(file) is BackupCheck.Valid)
        val photos = File(scratch, "large-photos").apply { mkdirs() }
        val extracted = BackupReader.extractPhoto(file, "IMG_large.jpg", photos) as PhotoExtraction.Extracted
        assertEquals(size, extracted.file.length())
        extracted.file.inputStream().buffered().use { input ->
            var position = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                for (i in 0 until n) {
                    if (buffer[i] != ((position + i) % 251).toByte()) throw AssertionError("byte ${position + i} differs")
                }
                position += n
            }
        }
    }

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
}
