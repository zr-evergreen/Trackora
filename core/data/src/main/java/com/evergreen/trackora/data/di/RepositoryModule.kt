package com.evergreen.trackora.data.di

import com.evergreen.trackora.data.repository.WorkEntryRepositoryImpl
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing repository implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindWorkEntryRepository(
        workEntryRepositoryImpl: WorkEntryRepositoryImpl
    ): WorkEntryRepository
}

