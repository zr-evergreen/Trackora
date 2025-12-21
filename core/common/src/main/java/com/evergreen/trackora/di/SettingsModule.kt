package com.evergreen.trackora.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.evergreen.trackora.settings.CustomFieldsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val SETTINGS_DATASTORE_NAME = "settings_prefs"

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME)
        }
    }

    @Provides
    @Singleton
    fun provideCustomFieldsManager(
        @SettingsDataStore dataStore: DataStore<Preferences>
    ): CustomFieldsManager = CustomFieldsManager(dataStore)
}

