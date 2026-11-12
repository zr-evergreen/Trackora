package com.evergreen.trackora.backup

import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * The one spelling of each backup JSON text that [BackupFormat] allows.
 *
 * Encoding is done here by hand rather than by kotlinx-serialization, because
 * the bytes are part of the format: a library upgrade that changed how it
 * escapes a character would otherwise change format 1.
 *
 * Entries and settings (at most 4 MiB each) are decoded with
 * kotlinx-serialization's strict parser, and a reader then encodes what it
 * decoded and requires the identical text. That one comparison refuses
 * duplicated or reordered keys, whitespace, alternative escapes and number
 * spellings, and anything a lenient parser might have let through. The
 * manifest, which can be far larger, is read by [ManifestParser] instead,
 * which accepts the same single spelling without holding the text.
 */
internal object CanonicalJson {

    private val strict = Json

    /** A whole manifest's text, through [ManifestParser]: for tests and small manifests. */
    fun decodeManifest(text: String): BackupManifest =
        ManifestParser.parse(text.toByteArray(Charsets.UTF_8).inputStream(), Limits.V1)

    fun decodeEntry(text: String): BackupEntry = strict.decodeFromString(BackupEntry.serializer(), text)
    fun decodeSettings(text: String): BackupSettings = strict.decodeFromString(BackupSettings.serializer(), text)

    fun encode(manifest: BackupManifest): String = buildString { encode(manifest) { append(it) } }

    /**
     * The manifest's text in pieces of at most one file record each, so a
     * writer never holds all of it.
     */
    fun encode(manifest: BackupManifest, piece: (String) -> Unit) {
        piece(buildString {
            append("{\"format\":").string(manifest.format)
            append(",\"formatVersion\":").append(manifest.formatVersion)
            append(",\"createdAt\":").string(manifest.createdAt)
            append(",\"app\":{\"id\":").string(manifest.app.id)
            append(",\"versionName\":").string(manifest.app.versionName)
            append(",\"versionCode\":").append(manifest.app.versionCode)
            append("},\"databaseVersion\":").append(manifest.databaseVersion)
            append(",\"entryCount\":").append(manifest.entryCount)
            append(",\"photoCount\":").append(manifest.photoCount)
            append(",\"missingPhotoCount\":").append(manifest.missingPhotoCount)
            append(",\"idHighWaterMark\":").number(manifest.idHighWaterMark)
            append(",\"files\":[")
        })
        manifest.files.forEachIndexed { i, file ->
            piece(buildString {
                if (i > 0) append(',')
                append("{\"path\":").string(file.path)
                append(",\"size\":").append(file.size)
                append(",\"sha256\":").string(file.sha256)
                append('}')
            })
        }
        piece("]}")
    }

    fun encode(entry: BackupEntry): String = buildString {
        append("{\"id\":").append(entry.id)
        append(",\"title\":").string(entry.title)
        append(",\"description\":").string(entry.description)
        append(",\"quantity\":").number(entry.quantity)
        append(",\"status\":").string(entry.status.name)
        append(",\"date\":").string(entry.date)
        append(",\"customField1\":").string(entry.customField1)
        append(",\"customField2\":").string(entry.customField2)
        append(",\"customField3\":").string(entry.customField3)
        append(",\"photo\":")
        val photo = entry.photo
        if (photo == null) {
            append("null")
        } else {
            append("{\"file\":").string(photo.file)
            append(",\"originalUri\":").string(photo.originalUri)
            append(",\"missing\":").string(photo.missing?.name)
            append('}')
        }
        append('}')
    }

    fun encode(settings: BackupSettings): String = buildString {
        append("{\"customField1Name\":").string(settings.customField1Name)
        append(",\"customField2Name\":").string(settings.customField2Name)
        append(",\"customField3Name\":").string(settings.customField3Name)
        append(",\"reminderEnabled\":").append(settings.reminderEnabled)
        append(",\"theme\":").string(settings.theme.name)
        append(",\"language\":").string(settings.language.name)
        append('}')
    }

    private fun StringBuilder.number(value: Long?): StringBuilder =
        if (value == null) append("null") else append(value)

    private fun StringBuilder.string(value: String?): StringBuilder {
        if (value == null) return append("null")
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\b' -> append("\\b")
                c == '\u000C' -> append("\\f")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u00").append(HEX[c.code shr 4]).append(HEX[c.code and 0xF])
                else -> append(c)
            }
        }
        return append('"')
    }

    private const val HEX = "0123456789abcdef"

    /**
     * [bytes] as text, or null unless they are well-formed UTF-8. The usual
     * decoders replace bad bytes with U+FFFD; a backup's text must arrive
     * exactly or not at all.
     */
    fun utf8(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, offset, length))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /**
     * Whether [text] can be written as UTF-8 exactly: no unpaired surrogates.
     * Encoders silently turn one into `?`, which would change the user's text.
     */
    fun isExactText(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return false
                i += 2
            } else {
                if (Character.isLowSurrogate(c)) return false
                i++
            }
        }
        return true
    }
}
