package com.evergreen.trackora.backup

import java.io.ByteArrayOutputStream

/**
 * Converts between an entry's stored photo link and the photo file it points
 * to.
 *
 * The app stores FileProvider links of the form
 * `content://<applicationId>.fileprovider/external_files/<path>`, where
 * `external_files` is the root of the app's external files folder
 * (`app/src/main/res/xml/file_provider_paths.xml`). Camera photos and gallery
 * copies sit directly in that folder; a restore will put its photos under
 * `restored/<restoreId>/`. The other roots in that file (`my_images`,
 * `my_cache`) have never held photos: photo storage has used the external
 * files folder itself since photos were added (b844726).
 */
object PhotoLinks {

    /** The release and debug builds, whose links a backup may hold. */
    val TRACKORA_APP_IDS: Set<String> = setOf("com.evergreen.trackora", "com.evergreen.trackora.debug")

    private const val SCHEME = "content://"
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val ROOT = "external_files"
    private const val MAX_DEPTH = 8
    private val APP_ID = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /** What a stored link points to. */
    sealed interface Link {
        /** One of the app's photo files, at [path] relative to its external files folder. */
        data class AppFile(val path: String) : Link {
            val fileName: String get() = path.substringAfterLast('/')
        }

        /** Anything else: another app's link (an old gallery-picker one), or not a link the app makes. */
        data object Other : Link
    }

    /**
     * The file [uri] points to, if it is one of the app's own: a link from one
     * of [appIds] to a path under `external_files` whose every part is a
     * name [isValidName] allows. Never throws.
     */
    fun parse(uri: String, appIds: Set<String> = TRACKORA_APP_IDS): Link {
        if (!uri.startsWith(SCHEME)) return Link.Other
        val rest = uri.substring(SCHEME.length)
        val slash = rest.indexOf('/')
        if (slash < 0) return Link.Other
        val authority = rest.substring(0, slash)
        if (appIds.none { it + AUTHORITY_SUFFIX == authority }) return Link.Other
        val path = rest.substring(slash + 1)
        if (path.any { it == '?' || it == '#' }) return Link.Other
        val segments = path.split('/')
        if (segments.size < 2 || segments.size - 1 > MAX_DEPTH || segments[0] != ROOT) return Link.Other
        val names = segments.drop(1).map { percentDecode(it) ?: return Link.Other }
        if (!names.all { isValidName(it) }) return Link.Other
        return Link.AppFile(names.joinToString("/"))
    }

    /**
     * The link the app's FileProvider gives the file at [path] in the external
     * files folder, for the app [appId]. Every part of [path] must be a name
     * [isValidName] allows; such names need no escaping in a link.
     */
    fun uriOf(appId: String, path: String): String {
        require(APP_ID.matches(appId)) { "Not an application ID: $appId" }
        val segments = path.split('/')
        require(segments.size in 1..MAX_DEPTH && segments.all { isValidName(it) }) { "Not a photo path: ${BackupRules.printable(path)}" }
        return "$SCHEME$appId$AUTHORITY_SUFFIX/$ROOT/$path"
    }

    /** Whether [name] may name a photo in a backup. See [BackupFormat]. */
    fun isValidName(name: String): Boolean = BackupRules.isPhotoName(name)

    /**
     * [wanted], or if a name in [taken] already matches it ignoring case,
     * the first free one of `<base>-2<.ext>`, `<base>-3<.ext>`, ... Two photos
     * in different folders can share a file name, and a backup's photos sit
     * in one folder.
     */
    fun uniqueName(wanted: String, taken: Set<String>): String {
        require(isValidName(wanted)) { "Not a photo name: ${BackupRules.printable(wanted)}" }
        val lower = taken.mapTo(HashSet()) { it.lowercase() }
        if (wanted.lowercase() !in lower) return wanted
        val dot = wanted.lastIndexOf('.')
        val (base, extension) =
            if (dot > 0 && wanted.length - dot <= MAX_EXTENSION) wanted.substring(0, dot) to wanted.substring(dot)
            else wanted to ""
        for (n in 2..lower.size + 2) {
            val suffix = "-$n"
            val candidate = base.take(BackupFormat.MAX_PHOTO_NAME_LENGTH - suffix.length - extension.length) + suffix + extension
            if (isValidName(candidate) && candidate.lowercase() !in lower) return candidate
        }
        error("No free name for $wanted")
    }

    private const val MAX_EXTENSION = 16
    private const val HEX_DIGITS = "0123456789abcdef"

    /** Decodes `%XX` escapes as UTF-8, strictly; null if malformed. */
    private fun percentDecode(segment: String): String? {
        if ('%' !in segment) return segment
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (c == '%') {
                if (i + 2 >= segment.length) return null
                // Not Character.digit, which also takes other scripts' digits.
                val high = HEX_DIGITS.indexOf(segment[i + 1].lowercaseChar())
                val low = HEX_DIGITS.indexOf(segment[i + 2].lowercaseChar())
                if (high < 0 || low < 0) return null
                bytes.write(high * 16 + low)
                i += 3
            } else {
                if (c.code > 0x7E) return null
                bytes.write(c.code)
                i++
            }
        }
        return CanonicalJson.utf8(bytes.toByteArray())
    }
}
