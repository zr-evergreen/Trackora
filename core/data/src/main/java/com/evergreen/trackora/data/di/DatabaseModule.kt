package com.evergreen.trackora.data.di

import android.content.Context
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

    /**
     * The one database, opened the way [TrackoraDatabase.build] opens it.
     *
     * It used to fall back to destructive migration: any version without a
     * migration path — a forgotten migration, or an older build installed
     * over a newer one — deleted every entry and opened empty, silently. It
     * now fails instead, and the entries stay on disk. Storage errors and
     * corruption no longer delete it either; see [TrackoraDatabase.build].
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TrackoraDatabase = TrackoraDatabase.build(context)

    @Provides
    fun provideWorkEntryDao(database: TrackoraDatabase): WorkEntryDao {
        return database.workEntryDao()
    }
}
