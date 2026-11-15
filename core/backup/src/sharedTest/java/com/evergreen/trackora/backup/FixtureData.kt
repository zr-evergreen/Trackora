package com.evergreen.trackora.backup

import java.io.OutputStream
import java.time.Instant

/**
 * What the frozen example backups hold: synthetic data reaching every edge
 * the format must carry unchanged. Nothing here is real customer data.
 *
 * `fixtures/v1/valid.zip` was written from [entries], [settings] and [photos]
 * by [writeValid]; its tests check that it still reads back as exactly this,
 * and that today's writer still spells it byte for byte the same.
 */
object FixtureData {

    val createdAt: Instant = Instant.parse("2026-11-12T12:00:00Z")
    val app = BackupApp(id = "com.evergreen.trackora", versionName = "0.0.0-fixture", versionCode = 0)
    const val DATABASE_VERSION = 2

    const val CAMERA_PHOTO = "IMG_20261103_101500.jpg"
    const val GALLERY_PHOTO = "IMG_20261104_120000_123_gallery.png"

    /** Opaque bytes standing in for photos: a backup never looks inside them. */
    val photos: Map<String, ByteArray> = sortedMapOf(
        CAMERA_PHOTO to pseudoRandom(seed = 1, size = 4096, header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())),
        GALLERY_PHOTO to pseudoRandom(seed = 2, size = 2048, header = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())),
    )

    /** Every character JSON escapes, and some it must not touch. */
    const val CONTROL_TEXT =
        "\u0000 NUL, \t tab, \n LF, \r CR, \b BS, \u000C FF, \u001F US, \" quote, \\ backslash, / slash, " +
            "\u007F DEL,   LS,   PS, ﻿ BOM, 😀 emoji"

    /** Long, multi-line, in two scripts: far past the app's 500-character input limit. */
    val LONG_DESCRIPTION: String = buildString { for (i in 0 until 1000) append("Line $i, خط $i 🧵\n") }

    private fun appLink(name: String) = "content://com.evergreen.trackora.fileprovider/external_files/$name"
    private fun debugLink(name: String) = "content://com.evergreen.trackora.debug.fileprovider/external_files/$name"

    val entries: List<BackupEntry> = listOf(
        // Schema-valid but beyond anything the app's input screens allow.
        BackupEntry(
            id = Long.MIN_VALUE, title = "", description = null, quantity = Long.MIN_VALUE,
            status = BackupStatus.IN_PROGRESS, date = "-999999999-01-01",
            customField1 = null, customField2 = null, customField3 = null, photo = null,
        ),
        BackupEntry(
            id = -1, title = "  leading and trailing spaces  ", description = LONG_DESCRIPTION, quantity = -1,
            status = BackupStatus.COMPLETED, date = "0001-01-01",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.missing(appLink("IMG_20251226_090000.jpg"), MissingPhoto.NOT_FOUND),
        ),
        // Persian with a zero-width non-joiner and right-to-left marks; an emoji ZWJ sequence.
        BackupEntry(
            id = 0, title = "تعمیر لپ‌تاپ ‏مشتری‏", description = "📷✅ 👩🏽‍🔧 done", quantity = 0,
            status = BackupStatus.DELIVERED, date = "2026-11-03",
            customField1 = "مشتری", customField2 = "", customField3 = CONTROL_TEXT,
            photo = BackupPhoto.included(CAMERA_PHOTO, appLink(CAMERA_PHOTO)),
        ),
        // The same photo in a second entry is stored once.
        BackupEntry(
            id = 1, title = "Shared photo", description = null, quantity = Int.MAX_VALUE.toLong(),
            status = BackupStatus.DELIVERED, date = "9999-12-31",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.included(CAMERA_PHOTO, appLink(CAMERA_PHOTO)),
        ),
        // Decomposed and precomposed é, Ohm sign and Omega: no normalisation.
        BackupEntry(
            id = 2, title = "é and é", description = "Ω and Ω", quantity = Int.MIN_VALUE.toLong(),
            status = BackupStatus.IN_PROGRESS, date = "2024-02-29",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.included(GALLERY_PHOTO, debugLink(GALLERY_PHOTO)),
        ),
        BackupEntry(
            id = 5, title = "Old gallery-picker link", description = null, quantity = null,
            status = BackupStatus.COMPLETED, date = "2025-12-26",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.missing("content://com.android.providers.media.documents/document/image%3A1234", MissingPhoto.NOT_APP_FILE),
        ),
        BackupEntry(
            id = 100, title = "Empty stored link", description = null, quantity = 999_999,
            status = BackupStatus.COMPLETED, date = "2026-01-01",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.missing("", MissingPhoto.NOT_APP_FILE),
        ),
        BackupEntry(
            id = 101, title = "Unreadable photo", description = null, quantity = 1,
            status = BackupStatus.COMPLETED, date = "2026-01-02",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.missing(appLink("IMG_20260102_080000.jpg"), MissingPhoto.UNREADABLE),
        ),
        BackupEntry(
            id = Long.MAX_VALUE, title = "Largest ID", description = null, quantity = Long.MAX_VALUE,
            status = BackupStatus.IN_PROGRESS, date = "+999999999-12-31",
            customField1 = null, customField2 = null, customField3 = null,
            photo = BackupPhoto.missing(debugLink("restored/r1/IMG_20260103_090000.jpg"), MissingPhoto.TOO_LARGE),
        ),
    )

    val idHighWaterMark: Long = Long.MAX_VALUE

    val settings = BackupSettings(
        customField1Name = "رنگ",
        customField2Name = "",
        customField3Name = "Size / اندازه",
        reminderEnabled = false,
        theme = BackupTheme.DARK,
        language = BackupLanguage.FA,
    )

    val emptySettings = BackupSettings("", "", "", reminderEnabled = true, theme = BackupTheme.SYSTEM, language = BackupLanguage.SYSTEM)

    fun writeValid(out: OutputStream): BackupManifest =
        BackupWriter(out, createdAt, app, DATABASE_VERSION).use { writer ->
            writer.writeSettings(settings)
            entries.forEach(writer::writeEntry)
            photos.forEach { (name, bytes) -> writer.writePhoto(name) { bytes.inputStream() } }
            writer.finish(idHighWaterMark)
        }

    fun writeEmpty(out: OutputStream): BackupManifest =
        BackupWriter(out, createdAt, app, DATABASE_VERSION).use { writer ->
            writer.writeSettings(emptySettings)
            writer.finish(null)
        }

    fun pseudoRandom(seed: Int, size: Int, header: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(size)
        header.copyInto(out)
        var x = seed
        for (i in header.size until size) {
            x = x * 1103515245 + 12345
            out[i] = (x ushr 16).toByte()
        }
        return out
    }
}
