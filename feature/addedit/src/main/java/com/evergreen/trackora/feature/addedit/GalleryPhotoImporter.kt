package com.evergreen.trackora.feature.addedit

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a photo picked from the gallery into the app's own storage.
 *
 * The picker hands back a `content://` URI whose read permission lasts only
 * as long as the app's process. Storing that URI, as the entry used to, meant
 * the photo loaded once and then showed as an empty card after the app was
 * next restarted — the provider refused with "requires ACTION_OPEN_DOCUMENT".
 * A copy is the app's to keep, survives the original being deleted from the
 * gallery, and is stored exactly like a camera photo: in the same directory,
 * behind the same FileProvider.
 */
@Singleton
class GalleryPhotoImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** The copied photo's FileProvider URI, or null if it could not be copied. */
    suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(null) ?: return@withContext null
        val extension = context.contentResolver.getType(source)
            ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: DEFAULT_EXTENSION

        val copy = copyPhoto(
            open = { context.contentResolver.openInputStream(source) },
            dir = dir,
            name = galleryPhotoName(LocalDateTime.now(), extension)
        ) ?: return@withContext null

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy).toString()
    }

    private companion object {
        const val DEFAULT_EXTENSION = "jpg"
    }
}

/**
 * Writes the stream from [open] to [dir]/[name] and returns the file, or null
 * if the source could not be read. A failed copy leaves no partial file
 * behind, so an entry can never point at half an image.
 */
internal fun copyPhoto(open: () -> InputStream?, dir: File, name: String): File? {
    val target = File(dir, name)
    return try {
        val input = open() ?: return null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target
    } catch (e: IOException) {
        target.delete()
        null
    } catch (e: SecurityException) {
        target.delete()
        null
    }
}

/** Named like camera photos, with a suffix so the two are distinguishable on disk. */
internal fun galleryPhotoName(now: LocalDateTime, extension: String): String =
    "IMG_${now.format(NAME_FORMAT)}_gallery.$extension"

private val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)
