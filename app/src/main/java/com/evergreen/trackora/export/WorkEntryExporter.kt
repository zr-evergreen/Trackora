package com.evergreen.trackora.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a CSV export to app cache and hands back a shareable content URI.
 *
 * Cache rather than external storage: the file exists only to be passed to
 * whatever app the user picks from the share sheet, and writing to external
 * storage would mean a runtime permission for something the user never browses
 * to. The OS is free to reclaim the directory afterwards, which is the correct
 * lifetime for a throwaway export.
 */
@Singleton
class WorkEntryExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Writes [csv] and returns a content URI other apps may read.
     *
     * Older exports are cleared first so the cache does not accumulate a file
     * per tap, and so the share sheet's recent-files list is not full of
     * near-identical entries.
     */
    fun writeCsv(csv: String, today: LocalDate = LocalDate.now()): Uri {
        val dir = File(context.cacheDir, EXPORT_DIR).apply {
            deleteRecursively()
            mkdirs()
        }

        val file = File(dir, fileName(today))
        file.writeText(csv, Charsets.UTF_8)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Builds the share chooser intent for [uri].
     *
     * `FLAG_GRANT_READ_URI_PERMISSION` is what actually lets the receiving app
     * open the file; without it the share appears to work and the other app
     * reports a permission failure.
     */
    fun shareIntent(uri: Uri, chooserTitle: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun fileName(today: LocalDate): String =
        "trackora-${today.format(FILE_DATE_FORMAT)}.csv"

    private companion object {
        const val EXPORT_DIR = "exports"
        const val MIME_TYPE = "text/csv"
        val FILE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
