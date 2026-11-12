package com.evergreen.trackora.backup

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The content rules of [BackupFormat], shared by [BackupWriter] and
 * [BackupReader] so the two can never disagree about what a valid backup is.
 * Each check returns null when the value is fine, or what is wrong with it.
 */
internal object BackupRules {

    private val PHOTO_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,${BackupFormat.MAX_PHOTO_NAME_LENGTH - 1}}")
    private val SHA256 = Regex("[0-9a-f]{64}")

    fun isPhotoName(name: String): Boolean =
        PHOTO_NAME.matches(name) && ".." !in name && !name.endsWith(".")

    /** The photo name in an archive path `photos/<name>`, or null if [path] isn't one. */
    fun photoNameOf(path: String): String? =
        path.takeIf { it.startsWith(BackupFormat.PHOTOS_DIR) }
            ?.substring(BackupFormat.PHOTOS_DIR.length)
            ?.takeIf { isPhotoName(it) }

    /**
     * Whether an archive path could, if unpacked carelessly, land outside the
     * folder it is unpacked into, or confuse what is unpacked where: absolute,
     * a `.` or `..` part, a backslash or colon, or characters other than
     * printable ASCII.
     */
    fun isUnsafePath(path: String): Boolean =
        path.isEmpty() ||
            path.startsWith("/") ||
            path.any { it < ' ' || it > '~' || it == '\\' || it == ':' } ||
            path.split('/').any { it == "." || it == ".." }

    /** Readable in a message whatever [path] holds. */
    fun printable(path: String): String = buildString {
        for (c in path.take(160)) {
            if (c in ' '..'~') append(c) else append("\\u").append(Integer.toHexString(c.code).padStart(4, '0'))
        }
        if (path.length > 160) append('…')
    }

    /**
     * The database's own rules for a row: nothing about lengths or ranges,
     * which the database doesn't limit. [BackupEntry.status] is already one of
     * the stored names by its type.
     */
    fun entryProblem(entry: BackupEntry): String? {
        listOf(
            "title" to entry.title,
            "description" to entry.description,
            "customField1" to entry.customField1,
            "customField2" to entry.customField2,
            "customField3" to entry.customField3,
        ).forEach { (field, value) ->
            if (value != null && !CanonicalJson.isExactText(value)) {
                return "$field holds an unpaired surrogate, which can't be stored as UTF-8"
            }
        }
        if (!isAppDate(entry.date)) {
            return "date \"${printable(entry.date)}\" isn't one the app can read"
        }
        val photo = entry.photo ?: return null
        if (!CanonicalJson.isExactText(photo.originalUri)) {
            return "the photo link holds an unpaired surrogate, which can't be stored as UTF-8"
        }
        return when {
            photo.file != null && photo.missing == null ->
                if (isPhotoName(photo.file)) null else "photo name \"${printable(photo.file)}\" isn't allowed"
            photo.file == null && photo.missing != null -> null
            else -> "a photo must name either its file or why it's missing, not both or neither"
        }
    }

    /** Whether the app's type converter can read [date]: Converters parses with ISO_LOCAL_DATE. */
    private fun isAppDate(date: String): Boolean = try {
        LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        true
    } catch (e: DateTimeParseException) {
        false
    }

    fun settingsProblem(settings: BackupSettings): String? {
        listOf(settings.customField1Name, settings.customField2Name, settings.customField3Name).forEach {
            if (!CanonicalJson.isExactText(it)) return "a custom field name holds an unpaired surrogate"
        }
        return null
    }

    /** Everything about a manifest that can be checked from the manifest alone. */
    fun manifestProblem(manifest: BackupManifest, limits: Limits): String? {
        if (manifest.format != BackupFormat.FORMAT_ID) return "format isn't \"${BackupFormat.FORMAT_ID}\""
        if (manifest.formatVersion != BackupFormat.VERSION) return "formatVersion isn't ${BackupFormat.VERSION}"
        if (!isCanonicalInstant(manifest.createdAt)) return "createdAt isn't a time written as Instant.toString() writes it"
        appProblem(manifest.app)?.let { return it }
        if (manifest.databaseVersion < 1) return "databaseVersion is below 1"
        if (manifest.entryCount < 0 || manifest.photoCount < 0 || manifest.missingPhotoCount < 0) return "a count is negative"
        if (manifest.missingPhotoCount > manifest.entryCount) return "more missing photos than entries"
        manifest.idHighWaterMark?.let { if (it < 0) return "idHighWaterMark is negative, which SQLite never makes it" }

        val files = manifest.files
        if (files.size < 2 || files[0].path != BackupFormat.SETTINGS || files[1].path != BackupFormat.ENTRIES) {
            return "files doesn't start with ${BackupFormat.SETTINGS} and ${BackupFormat.ENTRIES}"
        }
        if (files.size + 1 > limits.files) return "more than ${limits.files} files"
        val photos = files.drop(2)
        if (photos.size != manifest.photoCount) return "photoCount is ${manifest.photoCount}, but files lists ${photos.size} photos"
        var previous: String? = null
        val seen = HashSet<String>()
        for (record in photos) {
            val name = photoNameOf(record.path) ?: return "\"${printable(record.path)}\" isn't a photo path the format allows"
            if (previous != null && name <= previous) return "photos aren't listed once each in ascending name order"
            if (!seen.add(name.lowercase())) return "two photo names differ only in capital letters"
            previous = name
        }
        for (record in files) {
            if (record.size < 0) return "${record.path} has a negative size"
            if (!SHA256.matches(record.sha256)) return "${record.path} has no valid SHA-256"
        }
        return null
    }

    /** The app fields: exact text, at most [BackupFormat.MAX_APP_TEXT_LENGTH] characters each. */
    fun appProblem(app: BackupApp): String? {
        if (!CanonicalJson.isExactText(app.id) || !CanonicalJson.isExactText(app.versionName)) {
            return "the app fields hold an unpaired surrogate"
        }
        if (app.id.length > BackupFormat.MAX_APP_TEXT_LENGTH || app.versionName.length > BackupFormat.MAX_APP_TEXT_LENGTH) {
            return "the app fields are longer than ${BackupFormat.MAX_APP_TEXT_LENGTH} characters"
        }
        return null
    }

    private fun isCanonicalInstant(text: String): Boolean = try {
        Instant.parse(text).toString() == text
    } catch (e: DateTimeParseException) {
        false
    }
}
