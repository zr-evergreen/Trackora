package com.evergreen.trackora.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * The frozen example backups in `fixtures/v1`, and what reading each must give.
 *
 * They were made once by [make] and committed. They are never regenerated:
 * they are the record of format 1, and every future version of the app must
 * read them exactly as [CASES] says. [make] stays here to show how each was
 * made.
 */
object Fixtures {

    /** [because] is part of the refusal's detail, so a fixture can't pass by being refused for some other reason. */
    class Case(val file: String, val expected: BackupProblem?, val what: String, val because: String = "")

    val CASES = listOf(
        Case("valid.zip", null, "every edge value FixtureData holds, two photos, one shared"),
        Case("empty.zip", null, "no entries, no photos, default settings, no ID counter"),
        Case("valid-all-deflated.zip", null, "valid.zip's files with every one deflated, photos too: the format allows either method"),
        Case("truncated-in-entries.zip", BackupProblem.DAMAGED, "cut off halfway through entries.jsonl", "doesn't end with a ZIP end record"),
        Case("truncated-in-central-directory.zip", BackupProblem.DAMAGED, "cut off inside the central directory, after every file's data", "doesn't end with a ZIP end record"),
        Case("truncated-end-record.zip", BackupProblem.DAMAGED, "missing only its last byte", "doesn't end with a ZIP end record"),
        Case("flipped-byte-entries.zip", BackupProblem.DAMAGED, "one bit flipped in entries.jsonl's data", "compressed data"),
        Case("flipped-byte-photo.zip", BackupProblem.DAMAGED, "one bit flipped in a photo's data", "CRC-32"),
        Case("flipped-byte-manifest.zip", BackupProblem.DAMAGED, "one bit flipped in manifest.json's data", "CRC-32"),
        Case("flipped-byte-settings.zip", BackupProblem.DAMAGED, "one bit flipped in settings.json's data", "CRC-32"),
        Case("tampered-entries.zip", BackupProblem.HASH_MISMATCH, "an entry's title changed, with a correct CRC-32 but the old SHA-256", "entries.jsonl doesn't match its SHA-256"),
        Case("newer-format.zip", BackupProblem.NEWER_FORMAT, "formatVersion 2 and a file format 1 doesn't have", "format 2"),
        Case("name-traversal.zip", BackupProblem.UNSAFE_NAME, "a file named photos/../../databases/trackora_database", "photos/../../databases/trackora_database"),
        Case("name-absolute.zip", BackupProblem.UNSAFE_NAME, "a file with an absolute path", "/data/data/"),
        Case("name-backslash.zip", BackupProblem.UNSAFE_NAME, "a file name using backslashes to climb out", "photos\\..\\.."),
        Case("duplicate-name.zip", BackupProblem.DUPLICATE_FILE, "the same photo name twice", "IMG_20261103_101500.jpg\" appears more than once"),
        Case("case-duplicate-name.zip", BackupProblem.DUPLICATE_FILE, "two photo names differing only in capital letters", "img_20261103_101500.jpg\" appears more than once"),
        Case("oversized-photo.zip", BackupProblem.TOO_LARGE, "a photo of 256 MiB + 1 byte (zeros, compressed to under 300 KB)", "photos/IMG_20261104_120000_123_gallery.png is larger than 268435456 bytes"),
        Case("lying-size.zip", BackupProblem.DAMAGED, "a photo recorded as 1,000 bytes that unpacks to 1 MiB", "unpacks to more than its recorded size"),
        Case("not-a-zip.zip", BackupProblem.NOT_A_ZIP, "a text file", "isn't a ZIP file"),
        Case("zip64.zip", BackupProblem.UNSUPPORTED_ZIP, "a file marked as needing ZIP64", "ZIP version 4.5"),
        Case("encrypted.zip", BackupProblem.UNSUPPORTED_ZIP, "files marked as encrypted", "encryption"),
        Case("trailing-data.zip", BackupProblem.DAMAGED, "extra bytes after the end record", "doesn't end with a ZIP end record"),
        Case("overlapping-files.zip", BackupProblem.DAMAGED, "two central-directory records pointing at the same data", "don't lie back to back"),
    )

    fun make(): Map<String, ByteArray> {
        val valid = ByteArrayOutputStream().also { FixtureData.writeValid(it) }.toByteArray()
        val empty = ByteArrayOutputStream().also { FixtureData.writeEmpty(it) }.toByteArray()
        val layout = itemsOf(valid)
        fun item(name: String) = layout.first { it.name == name }
        fun middleOf(name: String) = item(name).let { it.dataOffset + it.compressedSize / 2 }.toInt()
        fun flipped(at: Int) = valid.copyOf().also { it[at] = (it[at].toInt() xor 0x01).toByte() }
        val files = RawZip.unpack(valid)
        val camera = BackupFormat.PHOTOS_DIR + FixtureData.CAMERA_PHOTO
        val gallery = BackupFormat.PHOTOS_DIR + FixtureData.GALLERY_PHOTO
        val centralOffset = u32(valid, valid.size - 6).toInt()

        fun beforeManifest(name: String, data: ByteArray) = files.dropLast(1) + (name to data) + files.last()
        fun afterCamera(name: String, data: ByteArray): List<Pair<String, ByteArray>> {
            val i = files.indexOfFirst { it.first == camera }
            return files.take(i + 1) + (name to data) + files.drop(i + 1)
        }

        return linkedMapOf(
            "valid.zip" to valid,
            "empty.zip" to empty,
            "valid-all-deflated.zip" to RawZip.of(files),
            "truncated-in-entries.zip" to valid.copyOf(middleOf(BackupFormat.ENTRIES)),
            "truncated-in-central-directory.zip" to valid.copyOf(centralOffset + 30),
            "truncated-end-record.zip" to valid.copyOf(valid.size - 1),
            "flipped-byte-entries.zip" to flipped(middleOf(BackupFormat.ENTRIES)),
            "flipped-byte-photo.zip" to flipped(middleOf(camera)),
            "flipped-byte-manifest.zip" to flipped(middleOf(BackupFormat.MANIFEST)),
            "flipped-byte-settings.zip" to flipped(middleOf(BackupFormat.SETTINGS)),
            "tampered-entries.zip" to RawZip.of(files.map { (name, data) ->
                if (name == BackupFormat.ENTRIES) name to String(data, Charsets.UTF_8).replace("Shared photo", "Shared phot0").toByteArray(Charsets.UTF_8)
                else name to data
            }),
            "newer-format.zip" to RawZip.of(
                beforeManifest("notes.json", "{}".toByteArray()).map { (name, data) ->
                    if (name == BackupFormat.MANIFEST) name to String(data, Charsets.UTF_8).replace("\"formatVersion\":1,", "\"formatVersion\":2,").toByteArray(Charsets.UTF_8)
                    else name to data
                },
            ),
            "name-traversal.zip" to RawZip.of(beforeManifest("photos/../../databases/trackora_database", "x".toByteArray())),
            "name-absolute.zip" to RawZip.of(beforeManifest("/data/data/com.evergreen.trackora/databases/trackora_database", "x".toByteArray())),
            "name-backslash.zip" to RawZip.of(beforeManifest("photos\\..\\..\\databases\\trackora_database", "x".toByteArray())),
            "duplicate-name.zip" to RawZip.of(afterCamera(camera, FixtureData.photos.getValue(FixtureData.CAMERA_PHOTO))),
            "case-duplicate-name.zip" to RawZip.of(afterCamera(camera.lowercase(), FixtureData.photos.getValue(FixtureData.CAMERA_PHOTO))),
            "oversized-photo.zip" to oversizedPhoto(files, gallery),
            "lying-size.zip" to lyingSize(files, gallery),
            "not-a-zip.zip" to "This is a text file, not a backup.\n".toByteArray(),
            "zip64.zip" to RawZip.of(files) {
                items[0].versionNeeded = 45
                items[0].extra = byteArrayOf(0x01, 0x00, 0x10, 0x00) + ByteArray(16)
            },
            "encrypted.zip" to RawZip.of(files) { encryptFlag = true },
            "trailing-data.zip" to valid + "trailing bytes".toByteArray(),
            "overlapping-files.zip" to RawZip.of(files) { items[1].localOffset = 0 },
        ).also { check(it.keys.toList() == CASES.map { c -> c.file }) }
    }

    /** The gallery photo replaced by 256 MiB + 1 zero bytes, everything else consistent with it. */
    private fun oversizedPhoto(files: List<Pair<String, ByteArray>>, gallery: String): ByteArray {
        val size = BackupFormat.MAX_PHOTO_BYTES + 1
        val zeros = Zeros(size)
        return RawZip.of(withPhotoRecord(files, gallery, size, zeros.sha256)) {
            items.first { it.name == gallery }.apply {
                compressed = zeros.deflated
                crc = zeros.crc
                this.size = size
            }
        }
    }

    /** The gallery photo recorded everywhere as 1,000 zero bytes, but its data unpacks to 1 MiB of them. */
    private fun lyingSize(files: List<Pair<String, ByteArray>>, gallery: String): ByteArray {
        val declared = ByteArray(1000)
        return RawZip.of(withPhotoRecord(files, gallery, declared.size.toLong(), hex(MessageDigest.getInstance("SHA-256").digest(declared)))) {
            items.first { it.name == gallery }.apply {
                compressed = RawZip.deflate(ByteArray(1024 * 1024))
                crc = RawZip.crc32(declared)
                size = declared.size.toLong()
            }
        }
    }

    /** [files] with the manifest's record for [path] changed to [size] and [sha256]. */
    private fun withPhotoRecord(files: List<Pair<String, ByteArray>>, path: String, size: Long, sha256: String) =
        files.map { (name, data) ->
            if (name != BackupFormat.MANIFEST) return@map name to data
            val manifest = CanonicalJson.decodeManifest(String(data, Charsets.UTF_8))
            val changed = manifest.copy(files = manifest.files.map { if (it.path == path) it.copy(size = size, sha256 = sha256) else it })
            name to CanonicalJson.encode(changed).toByteArray(Charsets.UTF_8)
        }

    /** [size] zero bytes, deflated and hashed a megabyte at a time. */
    private class Zeros(size: Long) {
        val deflated: ByteArray
        val crc: Long
        val sha256: String

        init {
            val chunk = ByteArray(1024 * 1024)
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            val crc32 = CRC32()
            val digest = MessageDigest.getInstance("SHA-256")
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var left = size
            while (left > 0) {
                val n = minOf(left, chunk.size.toLong()).toInt()
                crc32.update(chunk, 0, n)
                digest.update(chunk, 0, n)
                deflater.setInput(chunk, 0, n)
                while (!deflater.needsInput()) out.write(buffer, 0, deflater.deflate(buffer))
                left -= n
            }
            deflater.finish()
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            deflater.end()
            deflated = out.toByteArray()
            crc = crc32.value
            sha256 = hex(digest.digest())
        }
    }

    private fun itemsOf(zip: ByteArray): List<ZipItem> {
        val file = File.createTempFile("fixture", ".zip")
        try {
            file.writeBytes(zip)
            return RandomAccessFile(file, "r").use { ZipLayout.read(it, Limits.V1) }
        } finally {
            file.delete()
        }
    }

    private fun u32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)
}
