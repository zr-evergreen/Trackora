package com.evergreen.trackora.data.local.database

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the fail-safe has refused the app's database, read from its marker
 * file without opening the database, and signalled by the fail-safe itself
 * the moment it refuses it. See [FailSafeOpenHelperFactory] for why a refused
 * database is never opened, and [DatabaseRecheck] for lifting it.
 *
 * Kept for each database file, by path, for the life of the process: a
 * database the fail-safe refused stays refused for every instance opened in
 * this process, even after the marker is moved (the reminder worker asks for
 * it at start-up), so a lifted refusal takes effect only in a new process.
 * Until then nothing may use the database.
 */
object DatabaseDamage {

    private class State {
        val refused = MutableStateFlow(false)

        @Volatile
        var lifted = false
    }

    private val states = ConcurrentHashMap<String, State>()

    private fun state(database: File): State = states.computeIfAbsent(database.absolutePath) { State() }

    private fun appDatabase(context: Context): File = context.getDatabasePath(TrackoraDatabase.DATABASE_NAME)

    /** True while the marker sits beside the database, so every use of it is refused. */
    fun isRefused(context: Context): Boolean = marker(appDatabase(context)).exists()

    /** True if the database is refused now, or was at any point since this process started. */
    fun isRefusedUntilRestart(context: Context): Boolean = isRefusedUntilRestart(appDatabase(context))

    /**
     * The same, as it changes: true as soon as the fail-safe refuses the
     * database, also while the app is running, and from then on until the
     * process ends.
     */
    fun refusal(context: Context): StateFlow<Boolean> = refusal(appDatabase(context))

    /** True once a recheck in this process has lifted the refusal; the app must still restart. */
    fun wasLiftedInThisProcess(context: Context): Boolean = state(appDatabase(context)).lifted

    /** When the damage was recorded, from the marker file's time, or null if there is no marker. */
    fun recordedAt(context: Context): Long? = marker(appDatabase(context)).takeIf { it.exists() }?.lastModified()

    internal fun isRefusedUntilRestart(database: File): Boolean {
        val state = state(database)
        if (marker(database).exists()) state.refused.value = true
        return state.refused.value
    }

    internal fun refusal(database: File): StateFlow<Boolean> {
        isRefusedUntilRestart(database)
        return state(database).refused.asStateFlow()
    }

    /** Called by the fail-safe as it refuses [database], once its marker is written or attempted. */
    internal fun recorded(database: File) {
        state(database).refused.value = true
    }

    /** Called by [DatabaseRecheck] once it has moved [database]'s marker. */
    internal fun lifted(database: File) {
        state(database).lifted = true
    }

    internal fun wasLiftedInThisProcess(database: File): Boolean = state(database).lifted

    private fun marker(database: File) = File(database.path + DAMAGED_SUFFIX)
}
