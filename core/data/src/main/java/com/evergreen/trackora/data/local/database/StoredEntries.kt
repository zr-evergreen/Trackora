package com.evergreen.trackora.data.local.database

import android.content.Context
import android.database.Cursor
import java.io.IOException

/**
 * A row of the entries table exactly as SQLite stores it.
 *
 * [id] and [quantity] are SQLite's 64-bit integers and [date] and [status]
 * the stored text: the Room entity's `Int` quantity and parsed date can't
 * carry every value the database can hold, and a backup must.
 */
data class StoredEntry(
    val id: Long,
    val title: String,
    val description: String?,
    val quantity: Long?,
    val status: String,
    val date: String,
    val customField1: String?,
    val customField2: String?,
    val customField3: String?,
    val photoUri: String?,
)

/** The database is refused (see [DatabaseDamage]), so it wasn't opened. */
class DatabaseRefusedException : IOException("The database is refused until it is recovered, so it wasn't read")

/** A stored value has a type the entries table never gives it, so it can't be read without changing it. */
class UnreadableEntryException(message: String) : IOException(message)

/**
 * Reads every entry for a backup, without writing anything.
 *
 * The database is opened only through Room's own helper, so it goes through
 * the same migrations and fail-safe as everywhere else, never a destructive
 * fallback; and it isn't opened at all while it is refused.
 */
object StoredEntries {

    private const val ENTRIES =
        "SELECT id, title, description, quantity, status, date, customField1, customField2, customField3, photoUri " +
            "FROM work_entries ORDER BY id"

    private const val HIGH_WATER_MARK = "SELECT seq FROM sqlite_sequence WHERE name = 'work_entries'"

    /**
     * Hands every row of the entries table to [onRow] in ascending ID order,
     * then returns SQLite's AUTOINCREMENT counter for the table, or null if it
     * has none yet.
     *
     * Rows and counter come from one transaction, so they are one consistent
     * moment: a save made meanwhile waits until it ends. Nothing is written in
     * it, and it is ended without being marked successful, so it can't commit
     * anything. [onRow] runs inside it and should be quick (it writes the
     * entries file of a backup).
     *
     * @throws DatabaseRefusedException if the database is refused, without opening it.
     * @throws UnreadableEntryException if a value's type would have to change to be read.
     */
    fun read(context: Context, database: TrackoraDatabase, onRow: (StoredEntry) -> Unit): Long? {
        val file = context.getDatabasePath(database.openHelper.databaseName ?: TrackoraDatabase.DATABASE_NAME)
        if (DatabaseDamage.isRefusedUntilRestart(file)) throw DatabaseRefusedException()

        val db = try {
            database.openHelper.writableDatabase
        } catch (e: IllegalStateException) {
            // The fail-safe records damage it finds while opening before it throws.
            if (DatabaseDamage.isRefusedUntilRestart(file)) throw DatabaseRefusedException()
            throw IOException("The database couldn't be opened; it was left as it is", e)
        }
        db.beginTransactionNonExclusive()
        try {
            db.query(ENTRIES).use { cursor ->
                while (cursor.moveToNext()) onRow(entryOf(cursor))
            }
            return db.query(HIGH_WATER_MARK).use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    if (cursor.getType(0) != Cursor.FIELD_TYPE_INTEGER) {
                        throw UnreadableEntryException("The entries table's ID counter isn't an integer")
                    }
                    cursor.getLong(0)
                }
            }
        } finally {
            db.endTransaction()
        }
    }

    private fun entryOf(c: Cursor): StoredEntry {
        val id = c.getLong(0)
        return StoredEntry(
            id = id,
            title = text(c, 1, id, "title", nullable = false)!!,
            description = text(c, 2, id, "description", nullable = true),
            quantity = integer(c, 3, id, "quantity"),
            status = text(c, 4, id, "status", nullable = false)!!,
            date = text(c, 5, id, "date", nullable = false)!!,
            customField1 = text(c, 6, id, "customField1", nullable = true),
            customField2 = text(c, 7, id, "customField2", nullable = true),
            customField3 = text(c, 8, id, "customField3", nullable = true),
            photoUri = text(c, 9, id, "photoUri", nullable = true),
        )
    }

    /** Text as stored. A number or blob in a text column would have to be converted, so it is refused. */
    private fun text(c: Cursor, column: Int, id: Long, field: String, nullable: Boolean): String? =
        when (c.getType(column)) {
            Cursor.FIELD_TYPE_STRING -> c.getString(column)
            Cursor.FIELD_TYPE_NULL -> if (nullable) null else throw UnreadableEntryException("Entry $id has no $field")
            else -> throw UnreadableEntryException("Entry $id's $field isn't stored as text")
        }

    /**
     * A 64-bit integer as stored. Text or a fraction would come out as 0 or
     * truncated, so it is refused instead.
     */
    private fun integer(c: Cursor, column: Int, id: Long, field: String): Long? =
        when (c.getType(column)) {
            Cursor.FIELD_TYPE_INTEGER -> c.getLong(column)
            Cursor.FIELD_TYPE_NULL -> null
            else -> throw UnreadableEntryException("Entry $id's $field isn't stored as an integer")
        }
}
