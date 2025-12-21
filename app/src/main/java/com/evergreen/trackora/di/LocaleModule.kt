package com.evergreen.trackora.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.evergreen.trackora.locale.LocaleManager
import com.evergreen.trackora.theme.ThemeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val LOCALE_DATASTORE_NAME = "locale_prefs"
private const val THEME_DATASTORE_NAME = "theme_prefs"

@Module
@InstallIn(SingletonComponent::class)
object LocaleModule {

    @Provides
    @Singleton
    @LocaleDataStore
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
        @LocaleDataStore dataStore: DataStore<Preferences>
    ): LocaleManager = LocaleManager(dataStore)
}

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {

    @Provides
    @Singleton
    @ThemeDataStore
    fun provideThemeDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(THEME_DATASTORE_NAME)
        }
    }

    @Provides
    @Singleton
    fun provideThemeManager(
        @ThemeDataStore dataStore: DataStore<Preferences>
    ): ThemeManager = ThemeManager(dataStore)
}

