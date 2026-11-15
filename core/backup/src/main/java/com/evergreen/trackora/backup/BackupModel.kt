package com.evergreen.trackora.backup

import kotlinx.serialization.Serializable

/** What `manifest.json` holds. [BackupFormat] defines each field. */
@Serializable
data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val createdAt: String,
    val app: BackupApp,
    val databaseVersion: Int,
    val entryCount: Long,
    val photoCount: Int,
    val missingPhotoCount: Long,
    val idHighWaterMark: Long?,
    val files: List<BackupFile>,
)

/** The app that wrote a backup. Information only. */
@Serializable
data class BackupApp(
    val id: String,
    val versionName: String,
    val versionCode: Long,
)

/** One file of the archive: its path, size in bytes and SHA-256 (lower-case hex). */
@Serializable
data class BackupFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

/**
 * One row of the entries table, exactly as the database holds it.
 *
 * The types are the database's, not the app's: [id] and [quantity] are
 * SQLite's 64-bit integers, and [date] is the stored text, so nothing the
 * database can hold is lost or rewritten on the way through a backup.
 */
@Serializable
data class BackupEntry(
    val id: Long,
    val title: String,
    val description: String?,
    val quantity: Long?,
    val status: BackupStatus,
    val date: String,
    val customField1: String?,
    val customField2: String?,
    val customField3: String?,
    val photo: BackupPhoto?,
)

/**
 * An entry's photo: either included ([file] names it under `photos/`), or not
 * ([missing] says why). [originalUri] is the entry's stored link, unchanged
 * either way, so a backup always records that the entry had a photo.
 */
@Serializable
data class BackupPhoto(
    val file: String?,
    val originalUri: String,
    val missing: MissingPhoto?,
) {
    companion object {
        fun included(file: String, originalUri: String) = BackupPhoto(file, originalUri, null)
        fun missing(originalUri: String, reason: MissingPhoto) = BackupPhoto(null, originalUri, reason)
    }
}

/** The status names the database stores. */
@Serializable
enum class BackupStatus { IN_PROGRESS, COMPLETED, DELIVERED }

/** Why an entry's photo isn't in the backup. */
@Serializable
enum class MissingPhoto {
    /** The link isn't to one of the app's own photo files, e.g. an old gallery-picker link. */
    NOT_APP_FILE,
    NOT_FOUND,
    UNREADABLE,
    /** Larger than [BackupFormat.MAX_PHOTO_BYTES]. */
    TOO_LARGE,
}

/** The app's settings, as the app sees them (defaults filled in). */
@Serializable
data class BackupSettings(
    val customField1Name: String,
    val customField2Name: String,
    val customField3Name: String,
    val reminderEnabled: Boolean,
    val theme: BackupTheme,
    val language: BackupLanguage,
)

@Serializable
enum class BackupTheme { SYSTEM, LIGHT, DARK }

@Serializable
enum class BackupLanguage { SYSTEM, EN, FA }
