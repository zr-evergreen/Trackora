package com.evergreen.trackora.theme

import android.content.SharedPreferences
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
 *
 * The choice lives in DataStore. A copy of it is also kept in [launchPrefs],
 * because the theme has to be in place before the first activity is created,
 * and only a synchronous read can do that: [applyLaunchTheme] reads the copy
 * from Application.onCreate in a few milliseconds, where a first DataStore
 * read took 160–190 ms on a Galaxy A20e. Applied any later, a theme other
 * than the phone's own recreated the activity on every cold start — a frame
 * in the wrong theme, a moment of black, then the right one.
 */
@Singleton
class ThemeManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val launchPrefs: SharedPreferences,
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

    /**
     * Applies the theme last applied, from the synchronous copy, before any
     * activity exists. Does nothing on the first launch that has no copy yet;
     * [applySavedTheme] then applies the stored choice and makes one.
     */
    fun applyLaunchTheme() {
        val saved = launchPrefs.getString(LAUNCH_THEME_KEY, null)
        val mode = AppThemeMode.entries.firstOrNull { it.name == saved } ?: return
        AppCompatDelegate.setDefaultNightMode(nightModeOf(mode))
    }

    fun applyTheme(mode: AppThemeMode) {
        AppCompatDelegate.setDefaultNightMode(nightModeOf(mode))
        if (launchPrefs.getString(LAUNCH_THEME_KEY, null) != mode.name) {
            launchPrefs.edit().putString(LAUNCH_THEME_KEY, mode.name).apply()
        }
    }

    private fun nightModeOf(mode: AppThemeMode): Int = when (mode) {
        AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

    companion object {
        /** The SharedPreferences file holding the synchronous copy. */
        const val LAUNCH_THEME_PREFS = "launch_theme"
        private const val LAUNCH_THEME_KEY = "app_theme"
    }
}
