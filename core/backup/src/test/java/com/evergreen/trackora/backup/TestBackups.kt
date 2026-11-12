package com.evergreen.trackora.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Writing, reading and altering backups in tests. */
internal object TestBackups {

    val valid: ByteArray by lazy { ByteArrayOutputStream().also { FixtureData.writeValid(it) }.toByteArray() }

    fun write(limits: Limits = Limits.V1, block: BackupWriter.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        BackupWriter(out, FixtureData.createdAt, FixtureData.app, FixtureData.DATABASE_VERSION, limits).use { it.block() }
        return out.toByteArray()
    }

    fun check(bytes: ByteArray, limits: Limits = Limits.V1, onEntry: ((BackupEntry) -> Unit)? = null): BackupCheck {
        val file = File.createTempFile("backup", ".zip")
        try {
            file.writeBytes(bytes)
            return BackupReader.check(file, limits, onEntry)
        } finally {
            file.delete()
        }
    }

    fun assertValid(bytes: ByteArray, limits: Limits = Limits.V1): VerifiedBackup {
        val result = check(bytes, limits)
        assertTrue("expected valid, got $result", result is BackupCheck.Valid)
        return (result as BackupCheck.Valid).backup
    }

    fun assertRefused(expected: BackupProblem, bytes: ByteArray, limits: Limits = Limits.V1): String {
        val result = check(bytes, limits)
        assertTrue("expected $expected, got $result", result is BackupCheck.Invalid)
        assertEquals("$result", expected, (result as BackupCheck.Invalid).problem)
        return result.detail
    }

    fun assertWriterRefuses(expected: BackupProblem, block: () -> Unit): String {
        try {
            block()
        } catch (e: BackupException) {
            assertEquals(e.message, expected, e.problem)
            return e.message.orEmpty()
        }
        throw AssertionError("expected the writer to refuse with $expected")
    }

    fun entry(id: Long, photo: BackupPhoto? = null, title: String = "Entry $id", date: String = "2026-11-12") =
        BackupEntry(id, title, null, null, BackupStatus.COMPLETED, date, null, null, null, photo)

    fun link(name: String) = "content://com.evergreen.trackora.fileprovider/external_files/$name"

    fun included(name: String) = BackupPhoto.included(name, link(name))

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
}

/**
 * The files of a valid backup, to change and lay out again. [consistent]
 * rewrites the manifest's sizes and hashes to match the files, so a test
 * reaches the check it means to rather than tripping a hash first.
 */
internal class Craft(base: ByteArray = TestBackups.valid) {
    val files: MutableList<Pair<String, ByteArray>> = RawZip.unpack(base).toMutableList()

    fun bytesOf(path: String): ByteArray = files.first { it.first == path }.second
    fun textOf(path: String): String = String(bytesOf(path), Charsets.UTF_8)

    fun put(path: String, bytes: ByteArray): Craft {
        val i = files.indexOfFirst { it.first == path }
        if (i >= 0) files[i] = path to bytes else files.add(files.size - 1, path to bytes)
        return this
    }

    fun put(path: String, text: String) = put(path, text.toByteArray(Charsets.UTF_8))

    fun remove(path: String): Craft {
        files.removeAll { it.first == path }
        return this
    }

    fun entryLines(): List<String> = textOf(BackupFormat.ENTRIES).split('\n').dropLast(1)

    fun entries(lines: List<String>) = put(BackupFormat.ENTRIES, lines.joinToString("") { "$it\n" })

    fun manifest(): BackupManifest = CanonicalJson.decodeManifest(textOf(BackupFormat.MANIFEST))

    fun manifest(edit: (BackupManifest) -> BackupManifest) =
        put(BackupFormat.MANIFEST, CanonicalJson.encode(edit(manifest())))

    fun consistent(edit: (BackupManifest) -> BackupManifest = { it }): Craft = manifest { m ->
        edit(m.copy(files = m.files.map { record ->
            val bytes = files.firstOrNull { it.first == record.path }?.second ?: return@map record
            record.copy(size = bytes.size.toLong(), sha256 = TestBackups.sha256Hex(bytes))
        }))
    }

    fun build(configure: RawZip.() -> Unit = {}): ByteArray = RawZip.of(files, configure)
}
