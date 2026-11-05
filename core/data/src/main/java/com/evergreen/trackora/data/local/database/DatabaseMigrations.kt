package com.evergreen.trackora.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every step a user's database has needed, from the first version to the
 * current one.
 *
 * ### The rules
 *
 * - **Never delete to upgrade.** There is no destructive fallback. If a
 *   version has no path here, Room refuses to open the database instead of
 *   wiping it: a failed launch can be fixed with an update, a deleted work
 *   log cannot.
 * - **One migration per version step,** added here and to [ALL] in the same
 *   change that raises [TrackoraDatabase.VERSION]. DatabaseMigrationsTest
 *   fails if a step is missing.
 * - **Commit the new schema.** Room writes each version's schema to
 *   `core/data/schemas`; the migration tests build old databases from those
 *   files, and CI fails if a build changes them.
 * - **Test it with real data,** in MigrationTest: the old version filled with
 *   entries, migrated, and every value read back.
 */
internal object DatabaseMigrations {

    /** Version 2 gave each entry an optional photo. Existing entries have none. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE work_entries ADD COLUMN photoUri TEXT")
        }
    }

    /** All of them, in version order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
