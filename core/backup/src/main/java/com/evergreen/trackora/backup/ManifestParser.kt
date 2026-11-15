package com.evergreen.trackora.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads `manifest.json` straight from its stream, accepting only the one
 * spelling [CanonicalJson] writes, field by field in the format's order.
 *
 * The manifest is the one backup document that can be large (up to
 * [BackupFormat.MAX_MANIFEST_BYTES]), so it is parsed without ever holding
 * its text: memory grows only with the records kept, every string is capped
 * as it is read, and reading stops as soon as more files are listed than a
 * backup may hold. Because only the canonical spelling is accepted, there is
 * no separate encode-and-compare step.
 */
internal class ManifestParser private constructor(private val input: InputStream) {

    private val buffer = ByteArray(8192)
    private var position = 0
    private var length = 0

    private fun next(): Int {
        if (position == length) {
            var n: Int
            do {
                n = input.read(buffer, 0, buffer.size)
            } while (n == 0)
            if (n < 0) return -1
            position = 0
            length = n
        }
        return buffer[position++].toInt() and 0xFF
    }

    private fun peek(): Int {
        val b = next()
        if (b >= 0) position--
        return b
    }

    private fun matches(literal: String): Boolean = literal.all { next() == it.code }

    private fun expect(literal: String, what: String) {
        if (!matches(literal)) invalid("$what isn't where the format puts it")
    }

    /** A string in canonical spelling, at most [maxChars] characters. */
    private fun string(field: String, maxChars: Int): String {
        if (next() != '"'.code) invalid("$field isn't a string")
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = next()
            when {
                b < 0 -> invalid("$field ends early")
                b == '"'.code -> break
                b == '\\'.code -> bytes.write(escape(field))
                b < 0x20 -> invalid("$field holds a control character that isn't escaped")
                else -> bytes.write(b)
            }
            // UTF-8 needs at most 3 bytes per UTF-16 character.
            if (bytes.size() > maxChars * 3) invalid("$field is longer than $maxChars characters")
        }
        val text = CanonicalJson.utf8(bytes.toByteArray()) ?: invalid("$field isn't UTF-8 text")
        if (text.length > maxChars) invalid("$field is longer than $maxChars characters")
        return text
    }

    /** The character an escape stands for, if it is the escape the encoder uses for it. */
    private fun escape(field: String): Int = when (next()) {
        '"'.code -> '"'.code
        '\\'.code -> '\\'.code
        'b'.code -> 0x08
        'f'.code -> 0x0C
        'n'.code -> 0x0A
        'r'.code -> 0x0D
        't'.code -> 0x09
        'u'.code -> {
            if (!matches("00")) invalid("$field has an escape the format doesn't use")
            val high = HEX.indexOf(next().toChar())
            val low = HEX.indexOf(next().toChar())
            val value = high * 16 + low
            if (high < 0 || low < 0 || value >= 0x20 || value in SHORT_ESCAPES) invalid("$field has an escape the format doesn't use")
            value
        }
        else -> invalid("$field has an escape the format doesn't use")
    }

    /** An integer in plain decimal: optional `-`, no leading zeros, no `-0`. */
    private fun long(field: String): Long {
        val digits = StringBuilder()
        if (peek() == '-'.code) digits.append(next().toChar())
        val first = peek()
        if (first !in '0'.code..'9'.code) invalid("$field isn't a number")
        if (first == '0'.code) {
            next()
            if (digits.isNotEmpty()) invalid("$field is -0")
            if (peek() in '0'.code..'9'.code) invalid("$field has a leading zero")
            return 0
        }
        while (peek() in '0'.code..'9'.code) {
            digits.append(next().toChar())
            if (digits.length > 20) invalid("$field is out of range")
        }
        return digits.toString().toLongOrNull() ?: invalid("$field is out of range")
    }

    private fun int(field: String): Int {
        val value = long(field)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalid("$field is out of range")
        return value.toInt()
    }

    private fun longOrNull(field: String): Long? =
        if (peek() == 'n'.code) {
            expect("null", field)
            null
        } else {
            long(field)
        }

    private fun manifest(limits: Limits, intern: (String) -> String): BackupManifest {
        // Every format version begins this way; anything else isn't a Trackora manifest.
        if (!matches(PREFIX)) notOurs()
        val formatVersion = long("formatVersion")
        if (formatVersion > BackupFormat.VERSION) {
            throw BackupException(
                BackupProblem.NEWER_FORMAT,
                "The backup is in format $formatVersion, from a newer version of Trackora; this one reads format ${BackupFormat.VERSION}",
            )
        }
        if (formatVersion != BackupFormat.VERSION.toLong()) invalid("formatVersion isn't ${BackupFormat.VERSION}")
        expect(",\"createdAt\":", "createdAt")
        val createdAt = string("createdAt", 64)
        expect(",\"app\":{\"id\":", "app.id")
        val id = string("app.id", BackupFormat.MAX_APP_TEXT_LENGTH)
        expect(",\"versionName\":", "app.versionName")
        val versionName = string("app.versionName", BackupFormat.MAX_APP_TEXT_LENGTH)
        expect(",\"versionCode\":", "app.versionCode")
        val versionCode = long("app.versionCode")
        expect("},\"databaseVersion\":", "databaseVersion")
        val databaseVersion = int("databaseVersion")
        expect(",\"entryCount\":", "entryCount")
        val entryCount = long("entryCount")
        expect(",\"photoCount\":", "photoCount")
        val photoCount = int("photoCount")
        expect(",\"missingPhotoCount\":", "missingPhotoCount")
        val missingPhotoCount = long("missingPhotoCount")
        expect(",\"idHighWaterMark\":", "idHighWaterMark")
        val idHighWaterMark = longOrNull("idHighWaterMark")
        expect(",\"files\":[", "files")
        val files = ArrayList<BackupFile>()
        if (peek() != ']'.code) {
            while (true) {
                // These records, one more, and manifest.json itself.
                if (files.size + 2 > limits.files) invalid("files lists more than ${limits.files - 1} files")
                expect("{\"path\":", "a file's path")
                val path = intern(string("a file's path", ZipLayout.MAX_NAME_BYTES))
                expect(",\"size\":", "a file's size")
                val size = long("a file's size")
                expect(",\"sha256\":", "a file's SHA-256")
                val sha256 = string("a file's SHA-256", 64)
                expect("}", "the end of a file record")
                files += BackupFile(path, size, sha256)
                if (peek() != ','.code) break
                next()
            }
        }
        expect("]}", "the end of the manifest")
        if (next() != -1) invalid("there is more after the manifest's end")
        return BackupManifest(
            format = BackupFormat.FORMAT_ID,
            formatVersion = BackupFormat.VERSION,
            createdAt = createdAt,
            app = BackupApp(id, versionName, versionCode),
            databaseVersion = databaseVersion,
            entryCount = entryCount,
            photoCount = photoCount,
            missingPhotoCount = missingPhotoCount,
            idHighWaterMark = idHighWaterMark,
            files = files,
        )
    }

    companion object {
        private const val PREFIX = "{\"format\":\"" + BackupFormat.FORMAT_ID + "\",\"formatVersion\":"
        private const val HEX = "0123456789abcdef"
        private val SHORT_ESCAPES = setOf(0x08, 0x09, 0x0A, 0x0C, 0x0D)

        /**
         * Parses a manifest from [input], which it reads to its end. Each
         * file path is passed through [intern], so a caller already holding
         * an equal string can keep one copy.
         */
        fun parse(input: InputStream, limits: Limits, intern: (String) -> String = { it }): BackupManifest =
            ManifestParser(input).manifest(limits, intern)

        private fun invalid(what: String): Nothing =
            throw BackupException(BackupProblem.INVALID_MANIFEST, "manifest.json: $what")

        private fun notOurs(): Nothing =
            throw BackupException(BackupProblem.NOT_A_TRACKORA_BACKUP, "manifest.json isn't a Trackora backup manifest")
    }
}
