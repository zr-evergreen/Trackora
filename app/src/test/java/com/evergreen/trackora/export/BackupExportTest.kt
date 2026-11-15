package com.evergreen.trackora.export

import com.evergreen.trackora.backup.BackupApp
import com.evergreen.trackora.backup.BackupCheck
import com.evergreen.trackora.backup.BackupEntry
import com.evergreen.trackora.backup.BackupLanguage
import com.evergreen.trackora.backup.BackupPhoto
import com.evergreen.trackora.backup.BackupReader
import com.evergreen.trackora.backup.BackupSettings
import com.evergreen.trackora.backup.BackupStatus
import com.evergreen.trackora.backup.BackupTheme
import com.evergreen.trackora.backup.BackupWriter
import com.evergreen.trackora.backup.MissingPhoto
import com.evergreen.trackora.backup.PhotoExtraction
import com.evergreen.trackora.backup.VerifiedBackup
import com.evergreen.trackora.data.local.database.DatabaseRefusedException
import com.evergreen.trackora.data.local.database.StoredEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch

/**
 * Backup export on the JVM: synthetic rows standing in for the database,
 * real photo files, the real writer and reader. BackupExportDeviceTest runs
 * it against a real database on Android.
 */
class BackupExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var work: File
    private lateinit var photos: File
    private var refused = false
    private var settings = BackupSettings("", "", "", true, BackupTheme.SYSTEM, BackupLanguage.SYSTEM)
    private var rows: List<StoredEntry> = emptyList()
    private var mark: Long? = null
    private var reads = 0
    private var duringRead: () -> Unit = {}

    @Before
    fun setUp() {
        work = File(temp.newFolder("no_backup"), "backup-export")
        photos = temp.newFolder("files")
    }

    private fun export() = BackupExport(
        workFolder = work,
        photoFolder = { photos },
        isRefused = { refused },
        readSettings = { settings },
        readEntries = { onRow ->
            reads++
            rows.forEach {
                onRow(it)
                duringRead()
            }
            mark
        },
        app = BackupApp("com.evergreen.trackora", "1.0.0-test", 7),
        databaseVersion = 2,
        now = { NOW },
        zone = { ZoneOffset.UTC },
    )

    private fun row(
        id: Long,
        title: String = "Entry $id",
        photo: String? = null,
        quantity: Long? = null,
        status: String = "COMPLETED",
        date: String = "2026-11-15",
        description: String? = null,
    ) = StoredEntry(id, title, description, quantity, status, date, null, null, null, photo)

    private fun link(path: String, app: String = "com.evergreen.trackora") = "content://$app.fileprovider/external_files/$path"

    private fun photo(path: String, bytes: ByteArray = path.toByteArray()): File =
        File(photos, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun build(exporter: BackupExport = export()): BackupBuild = runBlocking { exporter.build() }

    private fun ready(exporter: BackupExport = export()): BackupBuild.Ready {
        val result = build(exporter)
        assertTrue("expected a ready backup, got $result", result is BackupBuild.Ready)
        return result as BackupBuild.Ready
    }

    private fun readBack(file: File): Pair<VerifiedBackup, List<BackupEntry>> {
        val entries = ArrayList<BackupEntry>()
        val result = BackupReader.readEntries(file) { entries += it }
        assertTrue("$result", result is BackupCheck.Valid)
        return (result as BackupCheck.Valid).backup to entries
    }

    private fun workFolders(): List<String> = work.list()?.sorted().orEmpty()

    @Test
    fun anEmptyDatabaseMakesAValidBackupOfTheSettings() {
        settings = BackupSettings("رنگ", "", "Size", false, BackupTheme.DARK, BackupLanguage.FA)
        val ready = ready()
        assertEquals("Trackora-backup-2026-11-15-0930.zip", ready.fileName)
        assertEquals(BackupSummary(0, 0, 0), ready.summary)
        val (backup, entries) = readBack(ready.file)
        assertEquals(emptyList<BackupEntry>(), entries)
        assertEquals(settings, backup.settings)
        assertEquals(null, backup.manifest.idHighWaterMark)
        assertEquals(BackupApp("com.evergreen.trackora", "1.0.0-test", 7), backup.manifest.app)
        assertEquals("2026-11-15T09:30:00Z", backup.manifest.createdAt)
    }

    @Test
    fun everyIdQuantityAndTheIdCounterAreKeptAs64BitValues() {
        rows = listOf(
            row(Long.MIN_VALUE, quantity = Long.MIN_VALUE),
            row(-5, quantity = -1),
            row(0, quantity = null),
            row(Int.MAX_VALUE + 1L, quantity = Int.MAX_VALUE + 1L),
            row(Long.MAX_VALUE, quantity = Long.MAX_VALUE),
        )
        mark = Long.MAX_VALUE
        val (backup, entries) = readBack(ready().file)
        assertEquals(rows.map { it.id }, entries.map { it.id })
        assertEquals(rows.map { it.quantity }, entries.map { it.quantity })
        assertEquals(Long.MAX_VALUE, backup.manifest.idHighWaterMark)
    }

    @Test
    fun deletedIdsKeepTheirPlaceInTheCounter() {
        rows = listOf(row(1), row(2), row(7))
        mark = 12
        val (backup, entries) = readBack(ready().file)
        assertEquals(listOf(1L, 2L, 7L), entries.map { it.id })
        assertEquals(12L, backup.manifest.idHighWaterMark)
    }

    @Test
    fun textIsKeptExactly() {
        val long = buildString { repeat(20_000) { append("خط $it, line $it 🧵\n") } }
        rows = listOf(
            row(1, title = ""),
            row(2, title = "  spaces kept  ", description = long),
            row(3, title = "تعمیر لپ‌تاپ ‏مشتری‏ 👩🏽‍🔧", description = "é and é, Ω and Ω"),
            row(4, title = "\u0000\t\n\r\"\\/\u007F ", date = "-999999999-01-01"),
            row(5, title = "max date", date = "+999999999-12-31"),
        ).map { it.copy(customField1 = "مشتری", customField2 = "", customField3 = null) }
        mark = 5
        val (_, entries) = readBack(ready().file)
        assertEquals(rows.map { it.title }, entries.map { it.title })
        assertEquals(rows.map { it.description }, entries.map { it.description })
        assertEquals(rows.map { it.date }, entries.map { it.date })
        assertEquals(listOf("مشتری"), entries.map { it.customField1 }.distinct())
        assertEquals(listOf(""), entries.map { it.customField2 }.distinct())
        assertEquals(listOf<String?>(null), entries.map { it.customField3 }.distinct())
    }

    @Test
    fun everyEntryComesBackAsTheRowItWasReadFrom() {
        val shared = photo("IMG_20261115_090000.jpg")
        rows = listOf(
            row(1, photo = link(shared.name), quantity = 3, status = "IN_PROGRESS"),
            row(2, photo = null, status = "DELIVERED", description = "d"),
            row(3, photo = link(shared.name), status = "COMPLETED"),
        )
        mark = 3
        val (_, entries) = readBack(ready().file)
        val expected = rows.map {
            BackupEntry(it.id, it.title, it.description, it.quantity, BackupStatus.valueOf(it.status), it.date,
                it.customField1, it.customField2, it.customField3, it.photoUri?.let { uri -> BackupPhoto.included(shared.name, uri) })
        }
        assertEquals(expected, entries)
    }

    @Test
    fun photosAreIncludedOnceAndMissingOnesKeepTheirLinks() {
        val shared = photo("IMG_20261115_090000.jpg", ByteArray(5000) { it.toByte() })
        val gallery = photo("IMG_20261115_091500_123_gallery.png")
        val sameName = photo("restored/r1/IMG_20261115_090000.jpg", "another photo, same name".toByteArray())
        val unreadable = photo("IMG_unreadable.jpg").apply { assertTrue(setReadable(false)) }
        val huge = File(photos, "IMG_huge.jpg").apply { RandomAccessFile(this, "rw").use { it.setLength(256L * 1024 * 1024 + 1) } }
        val before = snapshot(photos)
        rows = listOf(
            row(1, photo = link(shared.name)),
            row(2, photo = link(shared.name, app = "com.evergreen.trackora.debug")),
            row(3, photo = link(gallery.name)),
            row(4, photo = link("restored/r1/${sameName.name}")),
            row(5, photo = link("IMG_gone.jpg")),
            row(6, photo = link(unreadable.name)),
            row(7, photo = link(huge.name)),
            row(8, photo = "content://com.android.providers.media.documents/document/image%3A12"),
            row(9, photo = ""),
            row(10, photo = link("IMG_gone.jpg")),
        )
        mark = 10
        try {
            val ready = ready()
            val (backup, entries) = readBack(ready.file)
            assertEquals(BackupSummary(10, 3, 6), ready.summary)
            assertEquals(
                listOf("photos/IMG_20261115_090000-2.jpg", "photos/IMG_20261115_090000.jpg", "photos/IMG_20261115_091500_123_gallery.png"),
                backup.photos.map { it.path },
            )
            assertEquals(
                listOf(
                    BackupPhoto.included("IMG_20261115_090000.jpg", rows[0].photoUri!!),
                    BackupPhoto.included("IMG_20261115_090000.jpg", rows[1].photoUri!!),
                    BackupPhoto.included("IMG_20261115_091500_123_gallery.png", rows[2].photoUri!!),
                    BackupPhoto.included("IMG_20261115_090000-2.jpg", rows[3].photoUri!!),
                    BackupPhoto.missing(rows[4].photoUri!!, MissingPhoto.NOT_FOUND),
                    BackupPhoto.missing(rows[5].photoUri!!, MissingPhoto.UNREADABLE),
                    BackupPhoto.missing(rows[6].photoUri!!, MissingPhoto.TOO_LARGE),
                    BackupPhoto.missing(rows[7].photoUri!!, MissingPhoto.NOT_APP_FILE),
                    BackupPhoto.missing("", MissingPhoto.NOT_APP_FILE),
                    BackupPhoto.missing(rows[9].photoUri!!, MissingPhoto.NOT_FOUND),
                ),
                entries.map { it.photo },
            )
            val out = temp.newFolder("extracted")
            for ((name, source) in listOf("IMG_20261115_090000.jpg" to shared, "IMG_20261115_090000-2.jpg" to sameName, gallery.name to gallery)) {
                val extracted = BackupReader.extractPhoto(ready.file, name, out) as PhotoExtraction.Extracted
                assertArrayEquals(name, source.readBytes(), extracted.file.readBytes())
            }
            assertEquals("the photos were only read", before, snapshot(photos))
        } finally {
            unreadable.setReadable(true)
        }
    }

    @Test
    fun aPhotoFolderThatIsUnavailableMakesAppPhotosUnreadable() {
        rows = listOf(row(1, photo = link("IMG_1.jpg")))
        mark = 1
        val exporter = BackupExport(work, { null }, { false }, { settings }, { onRow -> rows.forEach(onRow); mark },
            BackupApp("com.evergreen.trackora", "1", 1), 2, { NOW }, { ZoneOffset.UTC })
        val (_, entries) = readBack(ready(exporter).file)
        assertEquals(BackupPhoto.missing(link("IMG_1.jpg"), MissingPhoto.UNREADABLE), entries.single().photo)
    }

    @Test
    fun aRefusedDatabaseIsNeitherReadNorWrittenAround() {
        refused = true
        rows = listOf(row(1))
        assertEquals(BackupBuild.Refused, build())
        assertEquals(0, reads)
        assertFalse(work.exists())
    }

    @Test
    fun aDatabaseRefusedWhileBeingReadLeavesNothingBehind() {
        rows = listOf(row(1), row(2))
        duringRead = { throw DatabaseRefusedException() }
        assertEquals(BackupBuild.Refused, build())
        assertEquals(emptyList<String>(), workFolders())
    }

    @Test
    fun dataTheFormatCanNotHoldExactlyFailsTheBackup() {
        for (bad in listOf(row(1, status = "CANCELLED"), row(1, date = "2026-02-30"), row(1, title = "a\uD800b"))) {
            rows = listOf(bad)
            val result = build()
            assertTrue("$bad: $result", result is BackupBuild.Failed && result.reason == BackupFailure.DATA)
            assertEquals(emptyList<String>(), workFolders())
        }
        rows = listOf(row(5))
        mark = 4
        val result = build()
        assertTrue("$result", result is BackupBuild.Failed && result.reason == BackupFailure.DATA)
    }

    @Test
    fun aPhotoThatDisappearsBeforeItIsCopiedFailsTheBackupAndCleansUp() {
        val doomed = photo("IMG_doomed.jpg")
        rows = listOf(row(1, photo = link(doomed.name)), row(2))
        mark = 2
        // The photo is planned while row 1 is read, and gone by the time photos are copied.
        duringRead = { doomed.delete() }
        val result = build()
        assertTrue("$result", result is BackupBuild.Failed && result.reason == BackupFailure.PHOTO_CHANGED)
        assertEquals(emptyList<String>(), workFolders())
    }

    @Test
    fun aCancelledBackupLeavesNothingBehind() = runBlocking {
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        rows = (1L..50L).map { row(it) }
        mark = 50
        duringRead = {
            inside.countDown()
            release.await()
        }
        val exporter = export()
        // On its own thread: this one blocks below until the build is inside the database read.
        val job = async(Dispatchers.IO) { exporter.build() }
        inside.await()
        job.cancel()
        release.countDown()
        try {
            job.await()
            fail("expected the build to be cancelled")
        } catch (expected: CancellationException) {
        }
        assertEquals(emptyList<String>(), workFolders())
    }

    @Test
    fun foldersOfInterruptedExportsAreRemovedButNothingElse() {
        work.mkdirs()
        val leftover = File(work, "1731600000000-0badf00d").apply { mkdirs(); File(this, "Trackora-backup-x.zip").writeText("partial") }
        val other = File(work, "keep-me").apply { mkdirs(); File(this, "file").writeText("not ours") }
        val loose = File(work, "Trackora-backup-2026-01-01-0000.zip").apply { writeText("a backup a user might have put here") }
        val ready = ready()
        assertFalse(leftover.exists())
        assertTrue(other.exists() && File(other, "file").readText() == "not ours")
        assertEquals("a backup a user might have put here", loose.readText())
        assertTrue(ready.file.exists())
    }

    @Test
    fun aBackupThatFailsItsReadBackIsNeverOffered() {
        rows = listOf(row(1), row(2))
        mark = 2
        val exporter = export()
        exporter.beforeVerify = { file -> RandomAccessFile(file, "rw").use { it.seek(it.length() / 2); val b = it.read(); it.seek(it.length() / 2); it.write(b xor 0x55) } }
        val damaged = build(exporter)
        assertTrue("$damaged", damaged is BackupBuild.Failed && damaged.reason == BackupFailure.VERIFICATION)
        assertEquals(emptyList<String>(), workFolders())

        // A valid backup, but not the one written: it must not pass either.
        exporter.beforeVerify = { file ->
            file.outputStream().use { out ->
                BackupWriter(out, NOW, BackupApp("com.evergreen.trackora", "1.0.0-test", 7), 2).use { w ->
                    w.writeSettings(settings)
                    w.writeEntry(BackupEntry(1, "Entry 1", null, null, BackupStatus.COMPLETED, "2026-11-15", null, null, null, null))
                    w.finish(2)
                }
            }
        }
        val swapped = build(exporter)
        assertTrue("$swapped", swapped is BackupBuild.Failed && swapped.reason == BackupFailure.VERIFICATION)
        assertEquals(emptyList<String>(), workFolders())
    }

    @Test
    fun aSavedCopyIsCheckedByteForByte() {
        rows = listOf(row(1))
        mark = 1
        val exporter = export()
        val ready = ready(exporter)
        val expected = ready.file.readBytes()

        val good = ByteArrayOutputStream()
        val saved = runBlocking { exporter.save(ready, { good }, { good.toByteArray().inputStream() }) }
        assertEquals(BackupSave.Saved(ready.summary), saved)
        assertArrayEquals(expected, good.toByteArray())

        // A destination that loses the last byte, or keeps old bytes past the end.
        val short = object : ByteArrayOutputStream() {
            fun content() = toByteArray().copyOf(size() - 1)
        }
        val lossy = runBlocking { exporter.save(ready, { short }, { short.content().inputStream() }) }
        assertTrue("$lossy", lossy is BackupSave.Failed && lossy.reason == BackupFailure.SAVE_VERIFICATION)
        val longer = ByteArrayOutputStream()
        val stale = runBlocking { exporter.save(ready, { longer }, { (longer.toByteArray() + byteArrayOf(1, 2, 3)).inputStream() }) }
        assertTrue("$stale", stale is BackupSave.Failed && stale.reason == BackupFailure.SAVE_VERIFICATION)

        val failing = object : OutputStream() {
            override fun write(b: Int) = throw IOException("disk full")
        }
        val refusedWrite = runBlocking { exporter.save(ready, { failing }, { null }) }
        assertTrue("$refusedWrite", refusedWrite is BackupSave.Failed && refusedWrite.reason == BackupFailure.SAVE)
        val noStream = runBlocking { exporter.save(ready, { null }, { null }) }
        assertTrue("$noStream", noStream is BackupSave.Failed && noStream.reason == BackupFailure.SAVE)
        // A provider that refuses with an unchecked exception is a failed save, not a crash.
        val providerError = runBlocking { exporter.save(ready, { throw IllegalArgumentException("mode not supported") }, { null }) }
        assertTrue("$providerError", providerError is BackupSave.Failed && providerError.reason == BackupFailure.SAVE)
        val unreadable = runBlocking { exporter.save(ready, { ByteArrayOutputStream() }, { throw SecurityException("no longer permitted") }) }
        assertTrue("$unreadable", unreadable is BackupSave.Failed && unreadable.reason == BackupFailure.SAVE)

        assertTrue(ready.file.exists())
        exporter.discard(ready)
        assertEquals(emptyList<String>(), workFolders())
    }

    @Test
    fun aDiscardedTicketRemovesABackupWhoseResultWasNeverSeen() {
        val exporter = export()
        val ticket = exporter.ticket()
        val ready = runBlocking { exporter.build(ticket) } as BackupBuild.Ready
        assertTrue(ready.file.exists())
        exporter.discard(ticket)
        assertEquals(emptyList<String>(), workFolders())
    }

    @Test
    fun moreFilesThanABackupHoldsIsTooLarge() {
        // settings.json, entries.jsonl and manifest.json leave room for 9,997 photos.
        rows = (1L..9_998L).map { id -> row(id, photo = link(photo("IMG_$id.jpg", byteArrayOf(id.toByte())).name)) }
        mark = 9_998
        val result = build()
        assertTrue("$result", result is BackupBuild.Failed && result.reason == BackupFailure.TOO_LARGE)
        assertEquals(emptyList<String>(), workFolders())
    }

    /** Every file's path, size, time and SHA-256. */
    private fun snapshot(folder: File): Map<String, String> =
        folder.walkTopDown().filter { it.isFile }.associate { file ->
            val sha = if (file.length() > 1024 * 1024 || !file.canRead()) "-" else MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            file.relativeTo(folder).path to "${file.length()} ${file.lastModified()} $sha"
        }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-15T09:30:00Z")
    }
}
