package com.evergreen.trackora.data.local.database

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Process
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Test-only. Runs in a process of its own (`:fresh`, see the androidTest
 * manifest) and opens a database there through the app's own builder, as the
 * app does after a restart: nothing of the test's process, the fail-safe's
 * memory of a refusal included, carries over. Replies with its pid and the
 * entries' titles in the order they were added, or the error it met.
 */
class FreshProcessProbe : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == OPEN) { "Unknown method $method" }
        val reply = Bundle().apply { putInt(PID, Process.myPid()) }
        val database = TrackoraDatabase.build(requireNotNull(context), requireNotNull(arg))
        try {
            val entries = runBlocking { database.workEntryDao().getAllEntries().first() }
            reply.putStringArrayList(TITLES, ArrayList(entries.sortedBy { it.id }.map { it.title }))
            database.close()
        } catch (e: Exception) {
            // Not closed: the fail-safe never closes a database it refused.
            reply.putString(ERROR, "${e.javaClass.name}: ${e.message}")
        }
        return reply
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val OPEN = "open"
        const val PID = "pid"
        const val TITLES = "titles"
        const val ERROR = "error"
    }
}
