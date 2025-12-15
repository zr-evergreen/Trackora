package com.evergreen.trackora.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.evergreen.trackora.locale.LocaleManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val LOCALE_DATASTORE_NAME = "locale_prefs"

@Module
@InstallIn(SingletonComponent::class)
object LocaleModule {

    @Provides
    @Singleton
    fun provideLocaleDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(LOCALE_DATASTORE_NAME)
        }
    }

    @Provides
    @Singleton
    fun provideLocaleManager(
        dataStore: DataStore<Preferences>
    ): LocaleManager = LocaleManager(dataStore)
}

