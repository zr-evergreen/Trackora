package com.evergreen.trackora.data.local.database

import android.content.Context
import java.io.File

/**
 * Whether the fail-safe has refused the app's database, read from its marker
 * file without opening the database. See [FailSafeOpenHelperFactory] for why
 * a refused database is never opened, and [DatabaseRecheck] for lifting it.
 */
object DatabaseDamage {

    @Volatile
    private var refusedInThisProcess = false

    /** True while the marker sits beside the database, so every use of it is refused. */
    fun isRefused(context: Context): Boolean = marker(context).exists()

    /**
     * True if the database is refused now, or was at any point since this
     * process started. A database the app has already asked for while it was
     * refused stays refused for the rest of the process, even after the
     * marker is moved (the reminder worker asks for it at start-up), so a
     * lifted refusal takes effect only in a new process. Until then nothing
     * may use the database.
     */
    fun isRefusedUntilRestart(context: Context): Boolean {
        if (isRefused(context)) refusedInThisProcess = true
        return refusedInThisProcess
    }

    /** When the damage was recorded, from the marker file's time, or null if there is no marker. */
    fun recordedAt(context: Context): Long? = marker(context).takeIf { it.exists() }?.lastModified()

    private fun marker(context: Context) =
        File(context.getDatabasePath(TrackoraDatabase.DATABASE_NAME).path + DAMAGED_SUFFIX)
}
