package com.evergreen.trackora.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.evergreen.trackora.data.local.converter.Converters
import com.evergreen.trackora.data.local.dao.WorkEntryDao
import com.evergreen.trackora.data.local.entity.WorkEntry

/**
 * Room database for the Trackora application.
 *
 * Its schema is exported, one JSON per version, to `core/data/schemas`; see
 * [DatabaseMigrations] for how a version is raised safely.
 */
@Database(
    entities = [WorkEntry::class],
    version = TrackoraDatabase.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TrackoraDatabase : RoomDatabase() {
    abstract fun workEntryDao(): WorkEntryDao

    companion object {
        const val DATABASE_NAME = "trackora_database"

        /** Raise only together with a migration to the new version in [DatabaseMigrations]. */
        const val VERSION = 2

        /**
         * The database as the app opens it, which nothing deletes:
         *
         * - Upgraded step by step through [DatabaseMigrations], with no
         *   destructive fallback. A version Room has no path for — a missing
         *   migration, or a database from a newer build — fails to open, and
         *   its entries stay on disk.
         * - Opened through [FailSafeOpenHelperFactory]. A storage error while
         *   opening fails that attempt and leaves the database as it is, and
         *   a database SQLite reports as corrupt is kept, copied aside and
         *   refused from then on, never replaced by an empty one.
         *
         * The tests open it through here too, so they check the configuration
         * users get.
         */
        fun build(context: Context, name: String = DATABASE_NAME): TrackoraDatabase =
            Room.databaseBuilder(context, TrackoraDatabase::class.java, name)
                .addMigrations(*DatabaseMigrations.ALL)
                .openHelperFactory(FailSafeOpenHelperFactory())
                .build()
    }
}
