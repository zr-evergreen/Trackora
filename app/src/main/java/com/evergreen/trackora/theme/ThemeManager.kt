package com.evergreen.trackora.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists and applies user-selected theme (light/dark/system).
 */
@Singleton
class ThemeManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val themeKey = stringPreferencesKey("app_theme")

    val themeFlow: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            AppThemeMode.LIGHT.name -> AppThemeMode.LIGHT
            AppThemeMode.DARK.name -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    suspend fun setTheme(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
        applyTheme(mode)
    }

    suspend fun applySavedTheme() {
        val current = themeFlow.firstOrNull() ?: AppThemeMode.SYSTEM
        applyTheme(current)
    }

    fun applyTheme(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}


