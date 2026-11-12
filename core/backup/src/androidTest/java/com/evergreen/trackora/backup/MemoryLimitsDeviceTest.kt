package com.evergreen.trackora.backup

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Peak memory of the reader and writer on the largest backups the limits allow,
 * and on crafted files built to exhaust memory, on Android's own heap: 48 MiB
 * per app on the API 24 emulator, 192 MiB on API 33+.
 *
 * Each case must finish without OutOfMemoryError while half the app's heap is
 * held back, as the app around the reader or writer would hold it. The file count is
 * [BackupFormat.MAX_FILES] unless the instrumentation argument `backupFiles`
 * sets another, which is how a lower limit is measured before it is chosen.
 * Synthetic data only, in the test APK's own cache folder.
 */
@RunWith(AndroidJUnit4::class)
class MemoryLimitsDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dir = File(instrumentation.targetContext.cacheDir, "memory-limits").apply { deleteRecursively(); mkdirs() }
    private val files = InstrumentationRegistry.getArguments().getString("backupFiles")?.toInt() ?: BackupFormat.MAX_FILES
    private val runtime = Runtime.getRuntime()
    private val mib = 1024L * 1024

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** M1: a 16 MiB manifest that is one long string, compressed to a few KiB. */
    @Test
    fun m1AStuffedManifestIsRefusedInLittleMemory() {
        val file = File(dir, "m1.zip")
        val prefix = "{\"format\":\"trackora-backup\",\"formatVersion\":1,\"createdAt\":\"2026-11-12T12:00:00Z\",\"app\":{\"id\":\"com.evergreen.trackora\",\"versionName\":\""
        StreamZip(file).apply {
            deflated(BackupFormat.MANIFEST) { out ->
                out.write(prefix.toByteArray())
                repeatBytes(out, 'x'.code.toByte(), BackupFormat.MAX_MANIFEST_BYTES.toLong() - prefix.length)
            }
            finish()
        }
        val result = measure("M1 stuffed 16 MiB manifest, ${file.length() / 1024} KiB file") { BackupReader.verify(file) }
        assertProblem(BackupProblem.INVALID_MANIFEST, result)
    }

    /** M1: the same file claiming a manifest one byte over the limit is refused unread. */
    @Test
    fun m1AManifestOverTheLimitIsRefusedUnread() {
        val file = File(dir, "m1-over.zip")
        StreamZip(file).apply {
            deflated(BackupFormat.MANIFEST) { out -> repeatBytes(out, 'x'.code.toByte(), BackupFormat.MAX_MANIFEST_BYTES + 1L) }
            finish()
        }
        val result = measure("M1 manifest over the limit, ${file.length() / 1024} KiB file") { BackupReader.verify(file) }
        assertProblem(BackupProblem.TOO_LARGE, result)
    }

    /** M2: an end record claiming a 64 MiB central directory, refused before any of it is read. */
    @Test
    fun m2AnOversizedCentralDirectoryIsRefusedUnread() {
        val file = File(dir, "m2.zip")
        val claimed = 64L * mib
        RandomAccessFile(file, "rw").use { raf ->
            raf.write("PK".toByteArray())
            raf.setLength(claimed + 22) // sparse: takes no space
            raf.seek(claimed)
            raf.write(endRecord(total = 1020, centralSize = claimed, centralOffset = 0))
        }
        val result = measure("M2 64 MiB central directory claimed") { BackupReader.verify(file) }
        assertProblem(BackupProblem.UNSUPPORTED_ZIP, result)
    }

    /** M2: a name longer than any a backup has is refused before it is copied. */
    @Test
    fun m2ALongNameIsRefusedBeforeItIsCopied() {
        val file = File(dir, "m2-name.zip")
        StreamZip(file).apply {
            // A short name first, so the central directory stays within its bound and the name check is what refuses it.
            stored(BackupFormat.SETTINGS)
            stored("photos/" + "A".repeat(ZipLayout.MAX_NAME_BYTES))
            finish()
        }
        val result = measure("M2 one 136-byte name") { BackupReader.verify(file) }
        assertProblem(BackupProblem.UNEXPECTED_FILE, result)
    }

    /** M3: the largest manifest the limits allow, listing [files] - 1 files with the longest names. */
    @Test
    fun m3TheLargestManifestIsReadWithinHalfTheHeap() {
        val file = File(dir, "m3.zip")
        val photos = files - 3
        StreamZip(file).apply {
            deflated(BackupFormat.MANIFEST) { out ->
                out.write(("{\"format\":\"trackora-backup\",\"formatVersion\":1,\"createdAt\":\"2026-11-12T12:00:00Z\"," +
                    "\"app\":{\"id\":\"${"i".repeat(256)}\",\"versionName\":\"${"v".repeat(256)}\",\"versionCode\":9223372036854775807}," +
                    "\"databaseVersion\":2,\"entryCount\":$photos,\"photoCount\":$photos,\"missingPhotoCount\":0,\"idHighWaterMark\":$photos," +
                    "\"files\":[{\"path\":\"settings.json\",\"size\":4194304,\"sha256\":\"${"a".repeat(64)}\"}," +
                    "{\"path\":\"entries.jsonl\",\"size\":4227858432,\"sha256\":\"${"b".repeat(64)}\"}").toByteArray())
                for (i in 0 until photos) {
                    out.write(",{\"path\":\"photos/${longestName(i)}\",\"size\":268435456,\"sha256\":\"${"c".repeat(64)}\"}".toByteArray())
                }
                out.write("]}".toByteArray())
            }
            finish()
        }
        // The archive holds only the manifest, so after reading it the reader finds settings.json missing.
        val result = measure("M3 manifest listing ${files - 1} files, ${file.length() / 1024} KiB file") { BackupReader.verify(file) }
        assertProblem(BackupProblem.MISSING_FILE, result)
    }

    /** M4: the largest central directory the limits allow: [files] files with the longest names. */
    @Test
    fun m4TheLargestCentralDirectoryIsReadWithinHalfTheHeap() {
        val file = File(dir, "m4.zip")
        StreamZip(file).apply {
            for (i in 0 until files) stored("photos/${longestName(i)}")
            finish()
        }
        val result = measure("M4 $files files with 135-byte names, ${file.length() / 1024} KiB file") { BackupReader.verify(file) }
        assertProblem(BackupProblem.NOT_A_TRACKORA_BACKUP, result)
    }

    /**
     * M5: the largest legitimate backup: [files] files, every photo with the
     * longest name, written by the writer and then verified in full.
     */
    @Test
    fun m5TheLargestBackupIsWrittenAndVerifiedWithinHalfTheHeap() {
        val file = File(dir, "m5.zip")
        val photos = files - 3
        measure("M5 writing $files files") {
            file.outputStream().buffered().use { out ->
                BackupWriter(out, FixtureData.createdAt, BackupApp("i".repeat(256), "v".repeat(256), Long.MAX_VALUE), 2).use { writer ->
                    writer.writeSettings(FixtureData.settings)
                    for (i in 0 until photos) {
                        val name = longestName(i)
                        writer.writeEntry(BackupEntry(i.toLong(), "Entry $i", null, i.toLong(), BackupStatus.DELIVERED, "2026-11-12",
                            null, null, null, BackupPhoto.included(name, PhotoLinks.uriOf("com.evergreen.trackora", name))))
                    }
                    for (i in 0 until photos) writer.writePhoto(longestName(i)) { byteArrayOf(i.toByte()).inputStream() }
                    writer.finish(photos.toLong())
                }
            }
            null
        }
        val result = measure("M5 verifying $files files, ${file.length() / 1024} KiB file") { BackupReader.verify(file) }
        // Never "$result" here: a valid result's text lists every photo, which is itself a huge string.
        if (result !is BackupCheck.Valid) throw AssertionError("expected a valid backup, got $result")
        assertEquals(photos, result.backup.photos.size)
    }

    /** Photo names of exactly the longest length, ascending with [i]. */
    private fun longestName(i: Int): String {
        val number = i.toString().padStart(8, '0')
        return "IMG_" + number + "_" + "x".repeat(BackupFormat.MAX_PHOTO_NAME_LENGTH - 4 - number.length - 1 - 4) + ".jpg"
    }

    /**
     * Runs [block] with half the heap held back by ballast, as the app around
     * the reader or writer would hold it: running out of memory fails the
     * test. The live peak is sampled right after forced collections, since
     * the heap's raw use also counts garbage not yet collected.
     */
    private fun measure(label: String, block: () -> BackupCheck?): BackupCheck? {
        val ballast = ArrayList<ByteArray>()
        repeat(3) {
            System.gc()
            System.runFinalization()
            Thread.sleep(50)
        }
        val before = used()
        repeat((runtime.maxMemory() / 2 / mib).toInt()) { ballast += ByteArray(mib.toInt()) }
        val held = used() - before
        val peak = AtomicLong(0)
        val running = AtomicBoolean(true)
        val sampler = Thread {
            while (running.get()) {
                runtime.gc()
                peak.accumulateAndGet(used() - before - held) { a, b -> maxOf(a, b) }
                Thread.sleep(20)
            }
        }.apply { start() }
        val started = System.nanoTime()
        val result = try {
            block()
        } catch (e: OutOfMemoryError) {
            running.set(false)
            ballast.clear()
            sampler.join()
            throw AssertionError("$label ran out of memory with half the ${runtime.maxMemory() / mib} MiB heap held back", e)
        } finally {
            running.set(false)
            sampler.join()
        }
        val line = "$label | heap ${runtime.maxMemory() / mib} MiB, ${held / mib} MiB held back | live peak +${"%.1f".format(peak.get() / mib.toDouble())} MiB | " +
            "${(System.nanoTime() - started) / 1_000_000} ms | ${(result as? BackupCheck.Invalid)?.problem ?: result?.let { "Valid" } ?: "written"}"
        ballast.clear()
        Log.i(TAG, line)
        return result
    }

    private fun used() = runtime.totalMemory() - runtime.freeMemory()

    private fun assertProblem(expected: BackupProblem, result: BackupCheck?) {
        if (result !is BackupCheck.Invalid || result.problem != expected) {
            throw AssertionError("expected $expected, got ${(result as? BackupCheck.Invalid) ?: "a valid backup"}")
        }
    }

    private fun repeatBytes(out: OutputStream, value: Byte, count: Long) {
        val chunk = ByteArray(64 * 1024) { value }
        var left = count
        while (left > 0) {
            val n = minOf(left, chunk.size.toLong()).toInt()
            out.write(chunk, 0, n)
            left -= n
        }
    }

    private fun endRecord(total: Int, centralSize: Long, centralOffset: Long): ByteArray {
        val b = java.nio.ByteBuffer.allocate(22).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.putInt(0x06054b50).putShort(0).putShort(0).putShort(total.toShort()).putShort(total.toShort())
        b.putInt(centralSize.toInt()).putInt(centralOffset.toInt()).putShort(0)
        return b.array()
    }

    /**
     * Writes a ZIP in the writer's layout straight to a file, keeping only
     * offsets in memory, so building the largest cases doesn't measure the test.
     */
    private class StreamZip(file: File) {
        private val out = FileOutputStream(file).buffered(1 shl 16)
        private var position = 0L
        private val central = File(file.path + ".central")
        private val centralOut = FileOutputStream(central).buffered(1 shl 16)
        private var count = 0

        private fun OutputStream.u16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
        private fun OutputStream.u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        private fun local(bytes: ByteArray) { out.write(bytes); position += bytes.size }

        private fun record(name: ByteArray, method: Int, crc: Long, compressedSize: Long, size: Long, offset: Long) {
            val version = if (method == 8) 20 else 10
            centralOut.apply {
                u32(0x02014b50); u16(version); u16(version); u16(if (method == 8) 0x0808 else 0x0800); u16(method)
                u16(0); u16(0x2821); u32(crc); u32(compressedSize); u32(size); u16(name.size); u16(0); u16(0)
                u16(0); u16(0); u32(0); u32(offset); write(name)
            }
            count++
        }

        /** An empty stored file. */
        fun stored(name: String) {
            val bytes = name.toByteArray(Charsets.ISO_8859_1)
            val offset = position
            val header = java.io.ByteArrayOutputStream().apply {
                u32(0x04034b50); u16(10); u16(0x0800); u16(0); u16(0); u16(0x2821); u32(0); u32(0); u32(0); u16(bytes.size); u16(0); write(bytes)
            }.toByteArray()
            local(header)
            record(bytes, 0, 0, 0, 0, offset)
        }

        /** A deflated file whose content [produce] streams out. */
        fun deflated(name: String, produce: (OutputStream) -> Unit) {
            val bytes = name.toByteArray(Charsets.ISO_8859_1)
            val offset = position
            local(java.io.ByteArrayOutputStream().apply {
                u32(0x04034b50); u16(20); u16(0x0808); u16(8); u16(0); u16(0x2821); u32(0); u32(0); u32(0); u16(bytes.size); u16(0); write(bytes)
            }.toByteArray())
            val crc = CRC32()
            var size = 0L
            val start = position
            val counting = object : OutputStream() {
                override fun write(b: Int) { out.write(b); position++ }
                override fun write(b: ByteArray, o: Int, l: Int) { out.write(b, o, l); position += l }
            }
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            val deflating = DeflaterOutputStream(counting, deflater, 1 shl 16)
            produce(object : OutputStream() {
                override fun write(b: Int) { crc.update(b); size++; deflating.write(b) }
                override fun write(b: ByteArray, o: Int, l: Int) { crc.update(b, o, l); size += l; deflating.write(b, o, l) }
            })
            deflating.finish()
            deflater.end()
            val compressedSize = position - start
            local(java.io.ByteArrayOutputStream().apply { u32(0x08074b50); u32(crc.value); u32(compressedSize); u32(size) }.toByteArray())
            record(bytes, 8, crc.value, compressedSize, size, offset)
        }

        fun finish() {
            centralOut.close()
            val centralStart = position
            central.inputStream().use { it.copyTo(out) }
            val centralSize = central.length()
            central.delete()
            out.apply {
                u32(0x06054b50); u16(0); u16(0); u16(count); u16(count); u32(centralSize); u32(centralStart); u16(0)
            }
            out.close()
        }
    }

    private companion object {
        const val TAG = "MemoryLimits"
    }
}
