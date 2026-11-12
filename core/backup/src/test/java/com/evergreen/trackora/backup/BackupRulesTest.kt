package com.evergreen.trackora.backup

import com.evergreen.trackora.backup.TestBackups.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {

    @Test
    fun photoNamesTheAppMakesAreAllowed() {
        listOf(
            "IMG_20261103_101500.jpg",
            "IMG_20261104_120000_123_gallery.png",
            "IMG_20261104_120000_123_gallery.webp",
            "a",
            "0",
            "A".repeat(128),
            "x-y_z.1.2",
        ).forEach { assertTrue(it, BackupRules.isPhotoName(it)) }
    }

    @Test
    fun otherPhotoNamesAreNot() {
        listOf(
            "", ".", "..", ".hidden", "-a.jpg", "_a.jpg", "a/b", "a\\b", "a..b", "a.", "A".repeat(129),
            "ä.jpg", "a b.jpg", "a:b", "a\u0000b", "IMG.jpg\n",
        ).forEach { assertFalse(it, BackupRules.isPhotoName(it)) }
    }

    @Test
    fun everyValueTheDatabaseCanHoldIsAllowed() {
        val unusual = listOf(
            entry(Long.MIN_VALUE, title = ""),
            entry(0, title = " "),
            entry(Long.MAX_VALUE, title = "x".repeat(1_000_000)),
            BackupEntry(1, "", "d".repeat(2_000_000), Long.MIN_VALUE, BackupStatus.IN_PROGRESS, "2026-11-12", "", "", "", null),
            BackupEntry(2, "", null, Long.MAX_VALUE, BackupStatus.COMPLETED, "2026-11-12", null, null, null, null),
            BackupEntry(3, "", null, 0, BackupStatus.DELIVERED, "2026-11-12", null, null, null, null),
            entry(4, BackupPhoto.missing("", MissingPhoto.NOT_APP_FILE)),
            entry(5, BackupPhoto.missing("not a link at all \u0000", MissingPhoto.NOT_FOUND)),
        )
        unusual.forEach { assertNull("$it", BackupRules.entryProblem(it)) }
    }

    @Test
    fun anyDateTheAppCanReadIsAllowed() {
        listOf("2026-11-12", "2024-02-29", "0001-01-01", "9999-12-31", "0000-01-01", "-0001-12-31",
            "-999999999-01-01", "+999999999-12-31", "+10000-01-01").forEach {
            assertNull(it, BackupRules.entryProblem(entry(1, date = it)))
        }
    }

    @Test
    fun aDateTheAppCannotReadIsNot() {
        listOf("", "2023-02-29", "2026-13-01", "2026-00-10", "2026-1-5", "20261112", "2026/11/12", " 2026-11-12",
            "2026-11-12 ", "2026-11-12T00:00", "١٤٠٥-٠٨-٢١", "10000-01-01", "+1000000000-01-01").forEach {
            assertNotNull(it, BackupRules.entryProblem(entry(1, date = it)))
        }
    }

    @Test
    fun textThatUtf8CannotHoldIsRefusedInEveryField() {
        val lone = "a\uD800b"
        val entries = listOf(
            entry(1, title = lone),
            entry(1).copy(description = lone),
            entry(1).copy(customField1 = lone),
            entry(1).copy(customField2 = lone),
            entry(1).copy(customField3 = lone),
            entry(1, BackupPhoto.missing(lone, MissingPhoto.NOT_FOUND)),
        )
        entries.forEach { assertNotNull("$it", BackupRules.entryProblem(it)) }
        assertNotNull(BackupRules.settingsProblem(BackupSettings(lone, "", "", true, BackupTheme.SYSTEM, BackupLanguage.SYSTEM)))
    }

    @Test
    fun aPhotoNamesItsFileOrWhyItIsMissingNotBothOrNeither() {
        assertNotNull(BackupRules.entryProblem(entry(1, BackupPhoto("IMG_1.jpg", "x", MissingPhoto.NOT_FOUND))))
        assertNotNull(BackupRules.entryProblem(entry(1, BackupPhoto(null, "x", null))))
        assertNotNull(BackupRules.entryProblem(entry(1, BackupPhoto("../x.jpg", "x", null))))
        assertNull(BackupRules.entryProblem(entry(1, BackupPhoto("IMG_1.jpg", "x", null))))
    }

    @Test
    fun unsafePathsAreRecognised() {
        listOf("/abs", "a/../b", "..", "photos/./x", "a\\b", "C:x", "a\u0000", "é", "photos/\u007F").forEach {
            assertTrue(it, BackupRules.isUnsafePath(it))
        }
        listOf("readme.txt", "photos/", "photos/sub/x.jpg", "photos/.hidden", "a//b").forEach {
            assertFalse(it, BackupRules.isUnsafePath(it))
        }
    }

    @Test
    fun manifestRules() {
        val good = Craft().manifest()
        assertNull(BackupRules.manifestProblem(good, Limits.V1))
        listOf(
            good.copy(createdAt = "2026-11-12T12:00:00.000Z"),
            good.copy(createdAt = "2026-11-12T12:00Z"),
            good.copy(createdAt = "yesterday"),
            good.copy(databaseVersion = 0),
            good.copy(entryCount = -1),
            good.copy(missingPhotoCount = good.entryCount + 1),
            good.copy(idHighWaterMark = -1),
            good.copy(photoCount = 3),
            good.copy(files = good.files.reversed()),
            good.copy(files = good.files.take(2) + good.files.drop(2).reversed()),
            good.copy(files = good.files.map { it.copy(sha256 = it.sha256.uppercase()) }),
            good.copy(files = good.files.map { it.copy(size = -1) }),
            good.copy(app = good.app.copy(versionName = "\uDC00")),
        ).forEach { assertNotNull("$it", BackupRules.manifestProblem(it, Limits.V1)) }
        assertEquals(
            "more than 4 files",
            BackupRules.manifestProblem(good, Limits.V1.copy(files = 4)),
        )
    }
}
