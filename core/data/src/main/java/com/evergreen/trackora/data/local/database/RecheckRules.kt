package com.evergreen.trackora.data.local.database

/** One column of `work_entries`, as `PRAGMA table_info` reports it. */
internal data class Column(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKey: Int,
)

/** A schema this app has shipped, as Room created it. */
internal data class Schema(
    val version: Int,
    val identityHash: String,
    val columns: List<Column>,
)

/**
 * Every schema the app has shipped, which a database it made can have.
 * ExpectedSchemaTest keeps this in step with the schema files Room exports to
 * core/data/schemas: a new database version needs its schema added here.
 */
internal object KnownSchemas {
    private val version1Columns = listOf(
        Column("id", "INTEGER", notNull = true, primaryKey = 1),
        Column("title", "TEXT", notNull = true, primaryKey = 0),
        Column("description", "TEXT", notNull = false, primaryKey = 0),
        Column("quantity", "INTEGER", notNull = false, primaryKey = 0),
        Column("status", "TEXT", notNull = true, primaryKey = 0),
        Column("date", "TEXT", notNull = true, primaryKey = 0),
        Column("customField1", "TEXT", notNull = false, primaryKey = 0),
        Column("customField2", "TEXT", notNull = false, primaryKey = 0),
        Column("customField3", "TEXT", notNull = false, primaryKey = 0),
    )

    val byVersion: Map<Int, Schema> = listOf(
        Schema(1, "9f1d1f92967f2b24c8ed84a7b8e2c7e3", version1Columns),
        Schema(2, "f4ccfc6f9438e6ac88d5ebbd47a90a0c", version1Columns + Column("photoUri", "TEXT", notNull = false, primaryKey = 0)),
    ).associateBy { it.version }
}

/** What the recheck read from its private copy of the database. */
internal data class CopyFacts(
    /** Every row `PRAGMA integrity_check` returned. */
    val integrity: List<String>,
    val userVersion: Int,
    /** Room's identity hash from `room_master_table`, or null if there is none. */
    val identityHash: String?,
    /** `work_entries` as `PRAGMA table_info` describes it; empty if there is no such table. */
    val columns: List<Column>,
    val indexCount: Int,
    val foreignKeyCount: Int,
    /**
     * Tables, views, triggers and indices in the database other than those
     * this app, Android or SQLite create (see [RecheckRules.isExpectedObject]),
     * as "type name".
     */
    val otherObjects: List<String> = emptyList(),
)

/** What the copy's facts say about the database. */
internal sealed interface Verdict {
    /** Structurally sound, with exactly a schema this app made. */
    data class Sound(val schema: Schema) : Verdict

    /** SQLite found problems: [problems] are the first rows `integrity_check` returned. */
    data class Damaged(val problems: List<String>) : Verdict

    /** Sound to SQLite, but not a database this app made, or not as it made it. */
    data class UnknownSchema(val reason: String) : Verdict
}

/**
 * Decides, from facts read off a private copy, whether a refused database may
 * be used again. Every condition must hold; anything else keeps the refusal.
 *
 * A [Verdict.Sound] database is structurally sound and has the shape this app
 * gives it. That does not prove its entries are complete or correct: an entry
 * lost before the damage was found, or a value that is wrong but well formed,
 * passes every check here.
 */
internal object RecheckRules {

    fun decide(facts: CopyFacts): Verdict {
        // Exactly one row, exactly "ok". Anything else is SQLite describing a problem.
        if (facts.integrity != listOf("ok")) return Verdict.Damaged(facts.integrity.take(5))

        val schema = KnownSchemas.byVersion[facts.userVersion]
            ?: return Verdict.UnknownSchema("user_version ${facts.userVersion} is not a version this app has shipped")
        if (facts.identityHash != schema.identityHash) {
            return Verdict.UnknownSchema(
                "Room identity hash ${facts.identityHash} does not match version ${schema.version}'s ${schema.identityHash}"
            )
        }
        if (facts.columns != schema.columns) {
            return Verdict.UnknownSchema("work_entries does not have version ${schema.version}'s columns")
        }
        if (facts.indexCount != 0 || facts.foreignKeyCount != 0) {
            return Verdict.UnknownSchema("work_entries has indices or foreign keys that no version of this app created")
        }
        if (facts.otherObjects.isNotEmpty()) {
            return Verdict.UnknownSchema("The database has objects no version of this app created: ${facts.otherObjects.take(5)}")
        }
        return Verdict.Sound(schema)
    }

    /**
     * Whether an entry of `sqlite_master` is one a database of this app has:
     * its two tables, Android's `android_metadata`, or a table or index
     * SQLite keeps for itself (`sqlite_sequence` for AUTOINCREMENT, and the
     * like), whose names no one else may use.
     */
    fun isExpectedObject(type: String, name: String): Boolean = when {
        name.startsWith("sqlite_") -> type == "table" || type == "index"
        type == "table" -> name in setOf("work_entries", "room_master_table", "android_metadata")
        else -> false
    }
}
