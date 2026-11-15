package com.evergreen.trackora.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The checks run on the frozen fixtures, the same on the JVM and on Android
 * devices: [load] is how each platform gets a fixture's bytes.
 */
class FixtureChecks(private val scratch: File, private val load: (String) -> ByteArray) {

    private fun fileOf(name: String): File = File(scratch, name).apply { writeBytes(load(name)) }

    /** Every fixture is accepted, or refused for exactly its listed reason. */
    fun everyFixtureReadsAsListed() {
        val wrong = Fixtures.CASES.mapNotNull { case ->
            val result = BackupReader.verify(fileOf(case.file))
            val ok = when (val expected = case.expected) {
                null -> result is BackupCheck.Valid
                else -> result is BackupCheck.Invalid && result.problem == expected && case.because in result.detail
            }
            if (ok) null else "${case.file} (${case.what}): expected ${case.expected ?: "valid"}, got $result"
        }
        if (wrong.isNotEmpty()) fail(wrong.joinToString("\n"))
    }

    /** valid.zip, and its all-deflated twin, hold exactly FixtureData: every entry, every setting, every photo byte. */
    fun validFixtureHoldsExactlyTheFixtureData() {
        validFixtureHoldsExactlyTheFixtureData("valid.zip")
        validFixtureHoldsExactlyTheFixtureData("valid-all-deflated.zip")
    }

    private fun validFixtureHoldsExactlyTheFixtureData(name: String) {
        val file = fileOf(name)
        val entries = ArrayList<BackupEntry>()
        val result = BackupReader.readEntries(file) { entries += it }
        assertTrue("$result", result is BackupCheck.Valid)
        val backup = (result as BackupCheck.Valid).backup

        assertEquals(FixtureData.entries, entries)
        assertEquals(FixtureData.settings, backup.settings)
        with(backup.manifest) {
            assertEquals(BackupFormat.FORMAT_ID, format)
            assertEquals(1, formatVersion)
            assertEquals("2026-11-12T12:00:00Z", createdAt)
            assertEquals(FixtureData.app, app)
            assertEquals(2, databaseVersion)
            assertEquals(9L, entryCount)
            assertEquals(2, photoCount)
            assertEquals(5L, missingPhotoCount)
            assertEquals(Long.MAX_VALUE, idHighWaterMark)
        }
        assertEquals(FixtureData.photos.keys.map { BackupFormat.PHOTOS_DIR + it }, backup.photos.map { it.path })

        val out = File(scratch, "photos-$name").apply { deleteRecursively(); mkdirs() }
        for ((name, bytes) in FixtureData.photos) {
            val extraction = BackupReader.extractPhoto(file, name, out)
            assertTrue("$extraction", extraction is PhotoExtraction.Extracted)
            assertArrayEquals(name, bytes, (extraction as PhotoExtraction.Extracted).file.readBytes())
        }
    }

    fun emptyFixtureHoldsNothing() {
        val entries = ArrayList<BackupEntry>()
        val result = BackupReader.readEntries(fileOf("empty.zip")) { entries += it }
        assertTrue("$result", result is BackupCheck.Valid)
        val backup = (result as BackupCheck.Valid).backup
        assertEquals(emptyList<BackupEntry>(), entries)
        assertEquals(FixtureData.emptySettings, backup.settings)
        assertEquals(0L, backup.manifest.entryCount)
        assertEquals(null, backup.manifest.idHighWaterMark)
        assertEquals(emptyList<BackupFile>(), backup.photos)
    }

    /**
     * The writer on this platform still spells FixtureData's JSON byte for
     * byte as the frozen file does: settings, entries and the manifest with
     * every size and hash. (The ZIP bytes around them may differ with the
     * platform's zlib; they are not part of the format's spelling.)
     */
    fun writerSpellsTheFixtureIdentically() {
        val frozen = RawZip.unpack(load("valid.zip"))
        val now = RawZip.unpack(ByteArrayOutputStream().also { FixtureData.writeValid(it) }.toByteArray())
        assertEquals(frozen.map { it.first }, now.map { it.first })
        for ((frozenFile, nowFile) in frozen.zip(now)) {
            assertArrayEquals(frozenFile.first, frozenFile.second, nowFile.second)
        }
        val expectedEntries = FixtureData.entries.joinToString("") { CanonicalJson.encode(it) + "\n" }
        assertArrayEquals(expectedEntries.toByteArray(Charsets.UTF_8), frozen.first { it.first == BackupFormat.ENTRIES }.second)
    }
}
