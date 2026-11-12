package com.evergreen.trackora.backup

/**
 * # The Trackora backup file, format 1
 *
 * One ZIP file. This comment is its complete definition: [BackupWriter] writes
 * exactly this, [BackupReader] accepts exactly this and refuses everything
 * else, and the example files in `core/backup/fixtures/v1` pin it down. Every
 * later version of the app must keep reading format 1. Any change to what a
 * backup holds or how it is spelled is a new format version, never an edit to
 * this one.
 *
 * ## Files, in this order
 *
 * 1. `settings.json`: the settings.
 * 2. `entries.jsonl`: every entry, one JSON object per line, each line ended
 *    by `\n` (the file is empty when there are no entries). IDs strictly
 *    ascending.
 * 3. `photos/<name>`: each photo an entry includes, stored once however many
 *    entries use it, names ascending (by `String.compareTo`, which for these
 *    ASCII names is byte order). A name is 1 to 128 characters from
 *    `A-Z a-z 0-9 . _ -`, starts with a letter or digit, contains no `..` and
 *    does not end with `.`. No two names differ only in capital letters:
 *    photo storage on Android ignores case.
 * 4. `manifest.json`: last. What the file holds and how to check it.
 *
 * ## manifest.json
 *
 * - `format`: `"trackora-backup"`.
 * - `formatVersion`: `1`. A reader refuses a higher version as made by a newer
 *   app, before reading anything else. Every version keeps these two keys.
 * - `createdAt`: when the backup was made, in UTC, exactly as
 *   `java.time.Instant.toString()` writes it.
 * - `app`: `{"id", "versionName", "versionCode"}` of the app that wrote it.
 *   Information only.
 * - `databaseVersion`: the database version the entries were read from.
 *   Information only: entries in format 1 always have the fields below.
 * - `entryCount`: lines in `entries.jsonl`.
 * - `photoCount`: files under `photos/`.
 * - `missingPhotoCount`: entries with a photo that could not be included.
 * - `idHighWaterMark`: SQLite's AUTOINCREMENT counter for the entries table
 *   (its `sqlite_sequence` value), or `null` when SQLite keeps none. SQLite
 *   never lets it go below 0 or below an ID in use, so neither may a backup.
 * - `files`: `[{"path", "size", "sha256"}]` for every other file, in archive
 *   order: the size in bytes and the SHA-256 in lower-case hex.
 *
 * ## entries.jsonl
 *
 * Each line holds one database row exactly. Only the rules the database itself
 * keeps apply; the app's input limits (title length, quantity range and so on)
 * are not format rules, so a backup can hold anything the database can.
 *
 * - `id`: any 64-bit integer.
 * - `title`: text, possibly empty.
 * - `description`: text or `null`.
 * - `quantity`: any 64-bit integer, or `null`.
 * - `status`: `IN_PROGRESS`, `COMPLETED` or `DELIVERED`, the names the database
 *   stores. The app cannot load an entry with any other.
 * - `date`: the stored text, character for character. It must parse with
 *   `DateTimeFormatter.ISO_LOCAL_DATE`, as the app reads it, or the app could
 *   not load the entry.
 * - `customField1`, `customField2`, `customField3`: text or `null`.
 * - `photo`: `null` when the entry has none. Otherwise
 *   `{"file", "originalUri", "missing"}`, where `originalUri` is the entry's
 *   stored link, unchanged, and either
 *   - `file` names the photo under `photos/` and `missing` is `null`, or
 *   - `file` is `null` and `missing` says why the photo isn't included:
 *     `NOT_APP_FILE` (the link isn't to one of the app's own photo files),
 *     `NOT_FOUND`, `UNREADABLE` or `TOO_LARGE`.
 *
 * ## settings.json
 *
 * - `customField1Name`, `customField2Name`, `customField3Name`: the custom
 *   field names, `""` when unset.
 * - `reminderEnabled`: `true` or `false`.
 * - `theme`: `SYSTEM`, `LIGHT` or `DARK`.
 * - `language`: `SYSTEM`, `EN` or `FA`.
 *
 * ## How the JSON is spelled
 *
 * Every value has exactly one accepted spelling, so a reader refuses
 * duplicated or reordered keys, extra spaces and alternative escapes, and
 * encoding the values read gives back the identical bytes:
 *
 * - UTF-8 with no byte-order mark. Keys in the order listed above, every key
 *   present (`null` when there is no value). No whitespace outside strings.
 * - Integers in plain decimal: an optional `-`, no leading zeros, no fraction
 *   or exponent. `true` and `false` for booleans.
 * - In strings, `"` and `\` are written `\"` and `\\`; backspace, form feed,
 *   line feed, carriage return and tab are written `\b`, `\f`, `\n`, `\r` and
 *   `\t`; any other character below U+0020 is written `\u00xx` with lower-case
 *   hex; every other character is written as itself. Text never holds an
 *   unpaired surrogate, which UTF-8 cannot represent.
 *
 * ## The ZIP container
 *
 * Only the plain subset `java.util.zip.ZipOutputStream` writes for files of
 * this size, so that no two programs can see different contents in one file:
 *
 * - Each file is DEFLATED or STORED. (The writer deflates the JSON files and
 *   stores the photos, which don't compress.) No encryption, ZIP64, comments
 *   or multiple disks.
 * - Files lie back to back from the first byte. The central directory follows
 *   the last one directly, and the end record closes the file, with nothing
 *   after it.
 * - A file's local header agrees with its central-directory record: name,
 *   method, flags, and the CRC-32 and sizes unless a data descriptor follows.
 *   A data descriptor carries its signature and agrees too.
 * - The archive holds the files the manifest lists, in its order, then
 *   `manifest.json`. Nothing else.
 *
 * Anything else is refused, even where other ZIP tools would open it: an
 * archive the writer could not have made is damaged, or was made by
 * something else.
 *
 * ## Limits
 *
 * [MAX_TOTAL_BYTES], [MAX_FILES], [MAX_PHOTO_BYTES], [MAX_ENTRY_LINE_BYTES],
 * [MAX_SETTINGS_BYTES] and [MAX_MANIFEST_BYTES]. The writer enforces the same
 * limits as the reader, so it never makes a file the reader refuses.
 */
object BackupFormat {
    const val FORMAT_ID = "trackora-backup"
    const val VERSION = 1

    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val ENTRIES = "entries.jsonl"
    const val PHOTOS_DIR = "photos/"

    private const val MIB = 1024L * 1024

    /**
     * All files together, unpacked: under 4 GiB. The 64 MiB kept free below
     * 4 GiB is room for the ZIP's own headers (under 25 MB even at
     * [MAX_FILES] files), so no size or offset in the archive ever needs ZIP64.
     */
    const val MAX_TOTAL_BYTES: Long = 4096 * MIB - 64 * MIB

    /** Files in the archive, `manifest.json` included. Below ZIP's 65,535. */
    const val MAX_FILES = 65_000

    const val MAX_PHOTO_BYTES: Long = 256 * MIB

    /**
     * One line of `entries.jsonl`, in bytes, without its line break. Above
     * Android's 2 MB cursor window, so any row the app can read fits.
     */
    const val MAX_ENTRY_LINE_BYTES: Int = (4 * MIB).toInt()

    const val MAX_SETTINGS_BYTES: Int = (4 * MIB).toInt()

    /** Room for [MAX_FILES] records with the longest names, twice over. */
    const val MAX_MANIFEST_BYTES: Int = (32 * MIB).toInt()

    const val MAX_PHOTO_NAME_LENGTH = 128
}

/**
 * The limits a writer and reader apply. Always [V1] outside tests; tests use
 * smaller ones to reach each limit's edge without gigabytes of data.
 */
internal data class Limits(
    val totalBytes: Long,
    val files: Int,
    val photoBytes: Long,
    val entryLineBytes: Int,
    val settingsBytes: Int,
    val manifestBytes: Int,
) {
    companion object {
        val V1 = Limits(
            totalBytes = BackupFormat.MAX_TOTAL_BYTES,
            files = BackupFormat.MAX_FILES,
            photoBytes = BackupFormat.MAX_PHOTO_BYTES,
            entryLineBytes = BackupFormat.MAX_ENTRY_LINE_BYTES,
            settingsBytes = BackupFormat.MAX_SETTINGS_BYTES,
            manifestBytes = BackupFormat.MAX_MANIFEST_BYTES,
        )
    }
}
