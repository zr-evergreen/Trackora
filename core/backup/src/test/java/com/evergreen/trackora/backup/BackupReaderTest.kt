package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.BackupProblem.DUPLICATE_FILE
import com.evergreen.trackora.backup.BackupProblem.HASH_MISMATCH
import com.evergreen.trackora.backup.BackupProblem.INCONSISTENT
import com.evergreen.trackora.backup.BackupProblem.INVALID_ENTRY
import com.evergreen.trackora.backup.BackupProblem.INVALID_MANIFEST
import com.evergreen.trackora.backup.BackupProblem.INVALID_SETTINGS
import com.evergreen.trackora.backup.BackupProblem.MISSING_FILE
import com.evergreen.trackora.backup.BackupProblem.NEWER_FORMAT
import com.evergreen.trackora.backup.BackupProblem.NOT_A_TRACKORA_BACKUP
import com.evergreen.trackora.backup.BackupProblem.TOO_LARGE
import com.evergreen.trackora.backup.BackupProblem.UNEXPECTED_FILE
import com.evergreen.trackora.backup.BackupProblem.UNSAFE_NAME
import com.evergreen.trackora.backup.TestBackups.assertRefused
import com.evergreen.trackora.backup.TestBackups.assertValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Everything about a backup's contents that must be refused, each for its own reason. */
class BackupReaderTest {

    private val camera = BackupFormat.PHOTOS_DIR + FixtureData.CAMERA_PHOTO
    private val gallery = BackupFormat.PHOTOS_DIR + FixtureData.GALLERY_PHOTO

    @Test
    fun theCraftedBaseIsValid() {
        assertValid(Craft().consistent().build())
    }

    // The manifest

    @Test
    fun noManifestIsNotATrackoraBackup() {
        assertRefused(NOT_A_TRACKORA_BACKUP, Craft().remove(BackupFormat.MANIFEST).build())
        assertRefused(NOT_A_TRACKORA_BACKUP, RawZip().build())
        assertRefused(NOT_A_TRACKORA_BACKUP, RawZip.of(listOf("readme.txt" to "hi".toByteArray())))
    }

    @Test
    fun someoneElsesManifestIsNotATrackoraBackup() {
        listOf(
            """{"format":"other-app","formatVersion":1}""",
            """{"formatVersion":1}""",
            """{"format":"trackora-backup"}""",
            """[1,2,3]""",
            """not json""",
            "",
            // Every version starts with exactly these bytes: reordered or escaped, they aren't ours.
            """{"formatVersion":1,"format":"trackora-backup"}""",
            """{"format":"trackora\u002dbackup","formatVersion":1}""",
            """{ "format":"trackora-backup","formatVersion":1}""",
        ).forEach { assertRefused(NOT_A_TRACKORA_BACKUP, Craft().put(BackupFormat.MANIFEST, it).build()) }
        assertRefused(NOT_A_TRACKORA_BACKUP, Craft().put(BackupFormat.MANIFEST, byteArrayOf(0xFF.toByte(), 0xFE.toByte())).build())
        // A byte-order mark: JSON parsers don't read past it, so it can't be anyone's manifest.
        assertRefused(NOT_A_TRACKORA_BACKUP, Craft().put(BackupFormat.MANIFEST, "\uFEFF" + Craft().textOf(BackupFormat.MANIFEST)).build())
    }

    @Test
    fun aNewerFormatIsRecognisedBeforeAnythingElse() {
        listOf(2L, 99_999_999_999L).forEach { version ->
            val text = Craft().textOf(BackupFormat.MANIFEST).replace("\"formatVersion\":1,", "\"formatVersion\":$version,")
            val detail = assertRefused(NEWER_FORMAT, Craft().put(BackupFormat.MANIFEST, text).put("new-thing/x.bin", "x").build())
            assertTrue(detail, "format $version" in detail)
        }
    }

    @Test
    fun aManifestNotSpelledTheOneWayIsRefused() {
        val text = Craft().textOf(BackupFormat.MANIFEST)
        listOf(
            text.replace(",\"createdAt\"", ", \"createdAt\""),
            "$text\n",
            text.replace("\"databaseVersion\":2", "\"databaseVersion\":2,\"databaseVersion\":2"),
            text.replace("\"databaseVersion\":2", "\"databaseVersion\":2.0"),
            text.replace("\"databaseVersion\":2", "\"databaseVersion\":02"),
            text.replace("\"databaseVersion\":2", "\"databaseVersion\":\"2\""),
            text.replace("\"idHighWaterMark\":", "\"extra\":1,\"idHighWaterMark\":"),
            text.replace(",\"idHighWaterMark\":9223372036854775807", ""),
            text.replace("\"0.0.0-fixture\"", "\"0.0.0\\u002dfixture\""),
            text.replace("\"0.0.0-fixture\"", "\"0.0.0\\/fixture\""),
            text.replace("\"0.0.0-fixture\"", "\"0.0.0\u0001fixture\""),
            text.replace("\"0.0.0-fixture\"", "\"0.0.0\\u001Ffixture\""),
            text.replace("\"0.0.0-fixture\"", "\"0.0.0\\u000afixture\""),
            text.replace("\"idHighWaterMark\":9223372036854775807", "\"idHighWaterMark\":-0"),
            text.replace("\"idHighWaterMark\":9223372036854775807", "\"idHighWaterMark\":9223372036854775808"),
            text.replace("\"idHighWaterMark\":9223372036854775807", "\"idHighWaterMark\":NULL"),
            """{"format":"trackora-backup","formatVersion":1,"x":[[[[1]]]]}""",
            """{"format":"trackora-backup","formatVersion":01,""" + text.substringAfter("\"formatVersion\":1,"),
            text.dropLast(1),
        ).forEach { assertRefused(INVALID_MANIFEST, Craft().put(BackupFormat.MANIFEST, it).build()) }
    }

    @Test
    fun manifestStringsAreCappedAsTheyAreRead() {
        val text = Craft().textOf(BackupFormat.MANIFEST)
        val longest = "v".repeat(BackupFormat.MAX_APP_TEXT_LENGTH)
        assertRefused(MISSING_FILE, Craft().put(BackupFormat.MANIFEST, text.replace("\"0.0.0-fixture\"", "\"$longest\"")).remove(BackupFormat.SETTINGS).build())
        listOf(
            text.replace("\"0.0.0-fixture\"", "\"${longest}v\""),
            text.replace("\"com.evergreen.trackora\"", "\"${"i".repeat(BackupFormat.MAX_APP_TEXT_LENGTH + 1)}\""),
            text.replace("\"2026-11-12T12:00:00Z\"", "\"${"9".repeat(65)}\""),
            text.replace("\"settings.json\"", "\"${"s".repeat(136)}\""),
        ).forEach { variant ->
            val detail = assertRefused(INVALID_MANIFEST, Craft().put(BackupFormat.MANIFEST, variant).build())
            assertTrue(detail, "longer than" in detail)
        }
    }

    @Test
    fun aManifestListingMoreFilesThanABackupHoldsStopsBeingRead() {
        // Five files in the archive, within a limit of six; the manifest lists ten.
        val crafted = Craft().manifest { m ->
            m.copy(files = m.files + (1..6).map { BackupFile("photos/IMG_x$it.jpg", 1, "0".repeat(64)) })
        }.build()
        val detail = assertRefused(INVALID_MANIFEST, crafted, Limits.V1.copy(files = 6))
        assertTrue(detail, "files lists more than 5 files" in detail)
    }

    /** M1: a small file declaring a manifest too large to hold, or one stuffed with a long string. */
    @Test
    fun m1AHugeManifestIsRefusedWithoutBeingHeld() {
        val prefix = Craft().textOf(BackupFormat.MANIFEST).substringBefore("\"0.0.0-fixture\"") + "\""
        val stuffed = prefix + "x".repeat(BackupFormat.MAX_MANIFEST_BYTES - prefix.length - 2) + "\"}"
        assertEquals(BackupFormat.MAX_MANIFEST_BYTES, stuffed.length)
        val compressed = Craft().put(BackupFormat.MANIFEST, stuffed).build()
        assertTrue("the crafted file is small: ${compressed.size}", compressed.size < 100_000)
        val detail = assertRefused(INVALID_MANIFEST, compressed)
        assertTrue(detail, "app.versionName is longer than 256 characters" in detail)

        val oversized = Craft().put(BackupFormat.MANIFEST, stuffed + " ").build()
        assertRefused(TOO_LARGE, oversized)
    }

    @Test
    fun aManifestBreakingItsOwnRulesIsRefused() {
        listOf<(BackupManifest) -> BackupManifest>(
            { it.copy(formatVersion = 0) },
            { it.copy(createdAt = "2026-11-12T12:00:00.000Z") },
            { it.copy(databaseVersion = 0) },
            { it.copy(idHighWaterMark = -1) },
            { it.copy(files = it.files.map { f -> f.copy(sha256 = "0".repeat(63)) }) },
        ).forEach { assertRefused(INVALID_MANIFEST, Craft().manifest(it).build()) }
    }

    // Names

    @Test
    fun unsafeNamesAreRefused() {
        listOf(
            "../x", "photos/../x", "/abs", "photos\\x", "C:x", "photos/./IMG_1.jpg", "photos/\u0001.jpg",
            "photos/\u00E9.jpg",
        ).forEach { assertRefused(UNSAFE_NAME, Craft().put(it, "x").build()) }
        val raw = Craft()
        assertRefused(UNSAFE_NAME, raw.put("photos/x", "x").build { items.first { it.name == "photos/x" }.nameBytes = "photos/\u00FF".toByteArray(Charsets.ISO_8859_1) })
    }

    @Test
    fun duplicateNamesAreRefused() {
        assertRefused(DUPLICATE_FILE, RawZip.of(Craft().files + (BackupFormat.MANIFEST to Craft().bytesOf(BackupFormat.MANIFEST))))
        assertRefused(DUPLICATE_FILE, Craft().put("SETTINGS.JSON", "{}").build())
        assertRefused(DUPLICATE_FILE, Craft().put("photos/img_20261103_101500.JPG", "x").build())
    }

    @Test
    fun filesTheFormatDoesNotHaveAreRefused() {
        listOf("readme.txt", "photos/", "photos/sub/IMG_1.jpg", "photos/.hidden", "Photos/IMG_9.jpg").forEach {
            assertRefused(UNEXPECTED_FILE, Craft().put(it, "x").build())
        }
    }

    // The archive against the manifest

    @Test
    fun aListedFileThatIsAbsentIsMissing() {
        assertRefused(MISSING_FILE, Craft().remove(gallery).build())
        assertRefused(MISSING_FILE, Craft().remove(BackupFormat.ENTRIES).build())
    }

    @Test
    fun anUnlistedPhotoIsUnexpected() {
        assertRefused(UNEXPECTED_FILE, Craft().put("photos/IMG_99.jpg", "x").build())
    }

    @Test
    fun filesOutOfOrderAreInconsistent() {
        val craft = Craft()
        val i = craft.files.indexOfFirst { it.first == camera }
        craft.files[i] = craft.files[i + 1].also { craft.files[i + 1] = craft.files[i] }
        assertRefused(INCONSISTENT, craft.build())
        val manifestFirst = Craft().files.let { listOf(it.last()) + it.dropLast(1) }
        assertRefused(INCONSISTENT, RawZip.of(manifestFirst))
    }

    @Test
    fun aSizeOrHashDifferentFromTheManifestIsAMismatch() {
        assertRefused(HASH_MISMATCH, Craft().manifest { m -> m.copy(files = m.files.map { if (it.path == camera) it.copy(size = it.size + 1) else it }) }.build())
        val changed = Craft().bytesOf(camera).copyOf().also { it[100] = (it[100] + 1).toByte() }
        assertRefused(HASH_MISMATCH, Craft().put(camera, changed).build())
        val settings = Craft().textOf(BackupFormat.SETTINGS).replace("\"DARK\"", "\"LITE\"")
        assertRefused(HASH_MISMATCH, Craft().put(BackupFormat.SETTINGS, settings).build())
    }

    @Test
    fun filesOverTheirLimitAreTooLarge() {
        val limits = Limits.V1.copy(manifestBytes = 100)
        assertRefused(TOO_LARGE, TestBackups.valid, limits)
    }

    // settings.json

    @Test
    fun badSettingsAreRefused() {
        val text = Craft().textOf(BackupFormat.SETTINGS)
        listOf(
            text.replace("\"DARK\"", "\"PURPLE\""),
            text.replace("\"FA\"", "\"fa\""),
            text.replace("false", "\"false\""),
            text.replace("\"reminderEnabled\":false", "\"reminderEnabled\":0"),
            text.replace(",\"theme\"", " ,\"theme\""),
            text.replace("\"customField2Name\":\"\"", "\"customField2Name\":\"\\ud800\""),
            text.replace("\"customField2Name\":\"\"", "\"customField2Name\":null"),
            text.replace("\"customField2Name\":\"\"", "\"customField2Name\":\"\\u0041\""),
            text.replace("}", ",\"x\":1}"),
        ).forEach { assertRefused(INVALID_SETTINGS, Craft().put(BackupFormat.SETTINGS, it).consistent().build()) }
        assertRefused(INVALID_SETTINGS, Craft().put(BackupFormat.SETTINGS, byteArrayOf('{'.code.toByte(), 0xC0.toByte(), 0x80.toByte())).consistent().build())
    }

    // entries.jsonl

    @Test
    fun anEntryLineNotSpelledTheOneWayIsRefused() {
        val line = Craft().entryLines()[3] // id 1
        listOf(
            line.replace(",\"title\"", ", \"title\""),
            line.replace("{\"id\":1,\"title\":\"Shared photo\"", "{\"title\":\"Shared photo\",\"id\":1"),
            line.replace("\"id\":1,", "\"id\":1,\"id\":1,"),
            line.replace("\"Shared photo\"", "\"Shared\\u0020photo\""),
            line.replace("\"Shared photo\"", "\"Shared\\/photo\""),
            line.replace("\"id\":1,", "\"id\":1.0,"),
            line.replace("\"id\":1,", "\"id\":01,"),
            line.replace("\"id\":1,", "\"id\":\"1\","),
            line.replace("\"description\":null,", ""),
            line.replace("\"photo\":{", "\"x\":0,\"photo\":{"),
            line.replace("\"title\":\"Shared photo\"", "\"title\":null"),
            line.replace("\"DELIVERED\"", "\"delivered\""),
            line.replace("\"DELIVERED\"", "\"CANCELLED\""),
            "$line ",
            "\uFEFF$line",
        ).forEach { variant ->
            val lines = Craft().entryLines().toMutableList().also { it[3] = variant }
            assertRefused(INVALID_ENTRY, Craft().entries(lines).consistent().build())
        }
    }

    @Test
    fun aMinusZeroIsNotZero() {
        val lines = Craft().entryLines().toMutableList()
        lines[2] = lines[2].replace("\"quantity\":0,", "\"quantity\":-0,")
        assertRefused(INVALID_ENTRY, Craft().entries(lines).consistent().build())
    }

    @Test
    fun entriesBreakingTheDatabaseRulesAreRefused() {
        fun refusedWith(change: (MutableList<String>) -> Unit, expected: BackupProblem = INVALID_ENTRY): String {
            val lines = Craft().entryLines().toMutableList().also(change)
            return assertRefused(expected, Craft().entries(lines).consistent().build())
        }
        refusedWith({ it[3] = it[3].replace("\"9999-12-31\"", "\"2023-02-29\"") })
        refusedWith({ it[3] = it[3].replace("\"Shared photo\"", "\"Shared \\udc00photo\"") })
        refusedWith({ it.add(4, it[3]) })
        refusedWith({ it[3] = it[4].also { _ -> it[4] = it[3] } })
        refusedWith({ it[3] = it[3].replace("\"missing\":null", "\"missing\":\"NOT_FOUND\"") })
        refusedWith({ it[3] = it[3].replace("\"file\":\"IMG_20261103_101500.jpg\"", "\"file\":\"../IMG.jpg\"") })
        refusedWith({ it.add(2, "") })
        val detail = refusedWith({ it[3] = it[3].replace("\"file\":\"IMG_20261103_101500.jpg\"", "\"file\":\"IMG_404.jpg\"") }, MISSING_FILE)
        assertTrue(detail, "IMG_404.jpg" in detail)
    }

    @Test
    fun anEntryLineThatIsNotUtf8IsRefused() {
        val bytes = Craft().bytesOf(BackupFormat.ENTRIES)
        val broken = byteArrayOf(0xC0.toByte(), 0x80.toByte()) + bytes
        assertRefused(INVALID_ENTRY, Craft().put(BackupFormat.ENTRIES, broken).consistent().build())
    }

    @Test
    fun aLastLineWithoutItsLineBreakIsRefused() {
        val text = Craft().textOf(BackupFormat.ENTRIES).dropLast(1)
        val detail = assertRefused(INVALID_ENTRY, Craft().put(BackupFormat.ENTRIES, text).consistent().build())
        assertTrue(detail, "line break" in detail)
    }

    @Test
    fun anEntryLineOverTheLimitIsTooLarge() {
        val limits = Limits.V1.copy(entryLineBytes = 1000)
        assertRefused(TOO_LARGE, TestBackups.valid, limits)
    }

    // Agreement between the parts

    @Test
    fun aPhotoNoEntryUsesIsInconsistent() {
        val lines = Craft().entryLines().map {
            it.replace("{\"file\":\"IMG_20261104_120000_123_gallery.png\",\"originalUri\":\"content://com.evergreen.trackora.debug.fileprovider/external_files/IMG_20261104_120000_123_gallery.png\",\"missing\":null}", "null")
        }
        val detail = assertRefused(INCONSISTENT, Craft().entries(lines).consistent().build())
        assertTrue(detail, FixtureData.GALLERY_PHOTO in detail)
    }

    @Test
    fun countsThatDisagreeAreInconsistent() {
        assertRefused(INCONSISTENT, Craft().manifest { it.copy(entryCount = it.entryCount + 1) }.build())
        assertRefused(INCONSISTENT, Craft().manifest { it.copy(missingPhotoCount = it.missingPhotoCount - 1) }.build())
    }

    @Test
    fun anIdCounterBelowAnIdIsInconsistent() {
        assertRefused(INCONSISTENT, Craft().manifest { it.copy(idHighWaterMark = Long.MAX_VALUE - 1) }.build())
    }

    @Test
    fun noIdCounterIsAllowed() {
        assertValid(Craft().manifest { it.copy(idHighWaterMark = null) }.build())
    }

    // Which problem wins

    @Test
    fun damageIsReportedRatherThanTheBadLineItCaused() {
        // Same length, so only the hash can tell; the manifest keeps the old one.
        val lines = Craft().entryLines().toMutableList().also { it[3] = "x".repeat(it[3].length) }
        val bytes = Craft().entries(lines).build()
        assertRefused(HASH_MISMATCH, bytes)
    }

    @Test
    fun entriesAreHandedOverInOrderAndTheResultSaysWhetherToTrustThem() {
        val seen = ArrayList<Long>()
        val result = TestBackups.check(TestBackups.valid) { seen += it.id }
        assertTrue(result is BackupCheck.Valid)
        assertEquals(FixtureData.entries.map { it.id }, seen)

        val damaged = Craft().put(camera, Craft().bytesOf(camera).copyOf().also { it[0] = 0 }).build()
        val partial = ArrayList<Long>()
        val refused = TestBackups.check(damaged) { partial += it.id }
        assertTrue(refused is BackupCheck.Invalid)
        assertEquals(FixtureData.entries.map { it.id }, partial)
    }
}
