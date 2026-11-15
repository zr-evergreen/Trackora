package com.evergreen.trackora.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExtractPhotoTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val camera = FixtureData.CAMERA_PHOTO

    private fun backupFile(bytes: ByteArray = TestBackups.valid) = temp.newFile().apply { writeBytes(bytes) }

    @Test
    fun aPhotoIsUnpackedExactly() {
        val dir = temp.newFolder()
        val result = BackupReader.extractPhoto(backupFile(), camera, dir)
        assertEquals(PhotoExtraction.Extracted(File(dir.canonicalFile, camera)), result)
        assertArrayEquals(FixtureData.photos.getValue(camera), File(dir, camera).readBytes())
        assertEquals(listOf(camera), dir.list()!!.toList())
    }

    @Test
    fun anExistingFileIsNeverReplaced() {
        val dir = temp.newFolder()
        val existing = File(dir, camera).apply { writeText("the user's own photo") }
        val result = BackupReader.extractPhoto(backupFile(), camera, dir)
        assertTrue("$result", result is PhotoExtraction.TargetExists)
        assertEquals("the user's own photo", existing.readText())
    }

    @Test
    fun anUnsafeNameTouchesNothing() {
        val backup = backupFile()
        val dir = temp.newFolder()
        val around = dir.parentFile!!.list()!!.toSet()
        listOf("../$camera", "/etc/passwd", "a/b.jpg", "", ".hidden").forEach { name ->
            val result = BackupReader.extractPhoto(backup, name, dir)
            assertTrue("$name: $result", result is PhotoExtraction.Refused && result.problem == BackupProblem.UNSAFE_NAME)
        }
        assertEquals(0, dir.list()!!.size)
        assertEquals(around, dir.parentFile!!.list()!!.toSet())
    }

    @Test
    fun aPhotoNotInTheBackupIsMissing() {
        val dir = temp.newFolder()
        val result = BackupReader.extractPhoto(backupFile(), "IMG_404.jpg", dir)
        assertTrue("$result", result is PhotoExtraction.Refused && result.problem == BackupProblem.MISSING_FILE)
        assertEquals(0, dir.list()!!.size)
    }

    @Test
    fun aDamagedOrChangedPhotoLeavesNoFileBehind() {
        val path = BackupFormat.PHOTOS_DIR + camera
        val changed = Craft().bytesOf(path).copyOf().also { it[10] = (it[10] + 1).toByte() }
        val cases = mapOf(
            BackupProblem.HASH_MISMATCH to Craft().put(path, changed).build(),
            BackupProblem.DAMAGED to Craft().build { items.first { it.name == path }.crc = 1 },
        )
        for ((problem, bytes) in cases) {
            val dir = temp.newFolder()
            val result = BackupReader.extractPhoto(backupFile(bytes), camera, dir)
            assertTrue("$problem: $result", result is PhotoExtraction.Refused && result.problem == problem)
            assertEquals("$problem left ${dir.list()!!.toList()}", 0, dir.list()!!.size)
        }
    }

    @Test
    fun anInvalidBackupExtractsNothing() {
        val dir = temp.newFolder()
        val result = BackupReader.extractPhoto(backupFile(TestBackups.valid.copyOf(100)), camera, dir)
        assertTrue("$result", result is PhotoExtraction.Refused && result.problem == BackupProblem.DAMAGED)
        assertEquals(0, dir.list()!!.size)
    }
}
