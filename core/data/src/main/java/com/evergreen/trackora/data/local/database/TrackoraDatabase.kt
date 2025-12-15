package com.evergreen.trackora.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.evergreen.trackora.data.local.converter.Converters
import com.evergreen.trackora.data.local.dao.WorkEntryDao
import com.evergreen.trackora.data.local.entity.WorkEntry

/**
 * Room database for the Trackora application.
 */
@Database(
    entities = [WorkEntry::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrackoraDatabase : RoomDatabase() {
    abstract fun workEntryDao(): WorkEntryDao
    
    companion object {
        const val DATABASE_NAME = "trackora_database"
    }
}

