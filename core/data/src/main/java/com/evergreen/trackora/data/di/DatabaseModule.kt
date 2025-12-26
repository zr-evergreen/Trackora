package com.evergreen.trackora.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.evergreen.trackora.data.local.dao.WorkEntryDao
import com.evergreen.trackora.data.local.database.TrackoraDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE work_entries ADD COLUMN photoUri TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TrackoraDatabase {
        return Room.databaseBuilder(
            context,
            TrackoraDatabase::class.java,
            TrackoraDatabase.DATABASE_NAME
        ).addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWorkEntryDao(database: TrackoraDatabase): WorkEntryDao {
        return database.workEntryDao()
    }
}

