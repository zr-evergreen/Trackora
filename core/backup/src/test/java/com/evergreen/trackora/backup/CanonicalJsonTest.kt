package com.evergreen.trackora.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalJsonTest {

    @Test
    fun anEntryIsSpelledExactlyAsTheFormatSays() {
        val entry = BackupEntry(
            id = -3, title = "Tea \"x\"", description = null, quantity = 0, status = BackupStatus.DELIVERED,
            date = "2026-11-12", customField1 = "", customField2 = null, customField3 = "a\\b",
            photo = BackupPhoto.included("IMG_1.jpg", "content://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg"),
        )
        assertEquals(
            """{"id":-3,"title":"Tea \"x\"","description":null,"quantity":0,"status":"DELIVERED","date":"2026-11-12",""" +
                """"customField1":"","customField2":null,"customField3":"a\\b",""" +
                """"photo":{"file":"IMG_1.jpg","originalUri":"content://com.evergreen.trackora.fileprovider/external_files/IMG_1.jpg","missing":null}}""",
            CanonicalJson.encode(entry),
        )
    }

    @Test
    fun aMissingPhotoAndNoPhotoAreSpelledExactly() {
        val missing = TestBackups.entry(1, BackupPhoto.missing("", MissingPhoto.NOT_APP_FILE))
        assertTrue(CanonicalJson.encode(missing).endsWith(""","photo":{"file":null,"originalUri":"","missing":"NOT_APP_FILE"}}"""))
        assertTrue(CanonicalJson.encode(TestBackups.entry(1)).endsWith(""","photo":null}"""))
    }

    @Test
    fun settingsAreSpelledExactly() {
        assertEquals(
            """{"customField1Name":"رنگ","customField2Name":"","customField3Name":"x","reminderEnabled":false,"theme":"DARK","language":"FA"}""",
            CanonicalJson.encode(BackupSettings("رنگ", "", "x", false, BackupTheme.DARK, BackupLanguage.FA)),
        )
    }

    @Test
    fun aManifestIsSpelledExactly() {
        val manifest = BackupManifest(
            format = "trackora-backup", formatVersion = 1, createdAt = "2026-11-12T12:00:00Z",
            app = BackupApp("com.evergreen.trackora", "1.0", 7), databaseVersion = 2,
            entryCount = 3, photoCount = 0, missingPhotoCount = 1, idHighWaterMark = null,
            files = listOf(BackupFile("settings.json", 10, "a".repeat(64)), BackupFile("entries.jsonl", 0, "b".repeat(64))),
        )
        assertEquals(
            """{"format":"trackora-backup","formatVersion":1,"createdAt":"2026-11-12T12:00:00Z",""" +
                """"app":{"id":"com.evergreen.trackora","versionName":"1.0","versionCode":7},"databaseVersion":2,""" +
                """"entryCount":3,"photoCount":0,"missingPhotoCount":1,"idHighWaterMark":null,""" +
                """"files":[{"path":"settings.json","size":10,"sha256":"${"a".repeat(64)}"},""" +
                """{"path":"entries.jsonl","size":0,"sha256":"${"b".repeat(64)}"}]}""",
            CanonicalJson.encode(manifest),
        )
    }

    @Test
    fun onlyWhatJsonRequiresIsEscaped() {
        val text = (0 until 0x20).map { it.toChar() }.joinToString("") + "\"\\/\u007F  ﻿😀é"
        val encoded = CanonicalJson.encode(BackupSettings(text, "", "", true, BackupTheme.SYSTEM, BackupLanguage.SYSTEM))
        val expected = "\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007\\b\\t\\n\\u000b\\f\\r\\u000e\\u000f" +
            "\\u0010\\u0011\\u0012\\u0013\\u0014\\u0015\\u0016\\u0017\\u0018\\u0019\\u001a\\u001b\\u001c\\u001d\\u001e\\u001f" +
            "\\\"\\\\/\u007F  ﻿😀é"
        assertEquals("""{"customField1Name":"$expected",""", encoded.substringBefore("\"customField2Name\""))
    }

    @Test
    fun everyCharacterRoundTripsExactly() {
        val all = (0..0xFFFF).map { it.toChar() }.filterNot { Character.isSurrogate(it) }.joinToString("") + "😀􏿿"
        val settings = BackupSettings(all, "", "", true, BackupTheme.SYSTEM, BackupLanguage.SYSTEM)
        val text = CanonicalJson.encode(settings)
        val bytes = text.toByteArray(Charsets.UTF_8)
        val decoded = CanonicalJson.decodeSettings(CanonicalJson.utf8(bytes)!!)
        assertEquals(settings, decoded)
        assertEquals(text, CanonicalJson.encode(decoded))
    }

    @Test
    fun theFixtureEntriesRoundTrip() {
        for (entry in FixtureData.entries) {
            val text = CanonicalJson.encode(entry)
            assertEquals(entry, CanonicalJson.decodeEntry(text))
        }
    }

    @Test
    fun exactTextMeansNoUnpairedSurrogates() {
        assertTrue(CanonicalJson.isExactText(""))
        assertTrue(CanonicalJson.isExactText("a😀b"))
        assertFalse(CanonicalJson.isExactText("\uD83D"))
        assertFalse(CanonicalJson.isExactText("\uDE00"))
        assertFalse(CanonicalJson.isExactText("\uDE00\uD83D"))
        assertFalse(CanonicalJson.isExactText("a\uD83Db"))
    }

    @Test
    fun utf8IsDecodedStrictly() {
        assertEquals("é😀", CanonicalJson.utf8("é😀".toByteArray(Charsets.UTF_8)))
        val malformed = listOf(
            byteArrayOf(0xFF.toByte()),
            byteArrayOf(0xC0.toByte(), 0x80.toByte()), // overlong NUL
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()), // an encoded surrogate
            byteArrayOf(0xE2.toByte(), 0x82.toByte()), // cut short
            byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()), // above U+10FFFF
        )
        for (bytes in malformed) assertNull(bytes.joinToString(), CanonicalJson.utf8(bytes))
    }

    @Test
    fun nestingIsCountedOutsideStringsOnly() {
        assertFalse(CanonicalJson.nestsDeeperThan("""{"a":[{"b":1}]}""", 3))
        assertTrue(CanonicalJson.nestsDeeperThan("""{"a":[{"b":[1]}]}""", 3))
        assertFalse(CanonicalJson.nestsDeeperThan("""{"a":"[[[[{{{{\"[[["}""", 1))
    }
}
