package com.evergreen.trackora.locale

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
 * Persists and applies user-selected locale.
 */
@Singleton
class LocaleManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val localeKey = stringPreferencesKey("app_locale")

    val localeFlow: Flow<AppLocale> = dataStore.data.map { prefs ->
        when (prefs[localeKey]) {
            AppLocale.EN.languageTag -> AppLocale.EN
            AppLocale.FA.languageTag -> AppLocale.FA
            else -> AppLocale.SYSTEM
        }
    }

    suspend fun setLocale(appLocale: AppLocale) {
        dataStore.edit { prefs ->
            prefs[localeKey] = appLocale.languageTag ?: ""
        }
        applyLocale(appLocale)
    }

    suspend fun applySavedLocale() {
        val current = localeFlow.firstOrNull() ?: AppLocale.SYSTEM
        applyLocale(current)
    }

    fun applyLocale(appLocale: AppLocale) {
        val localeList = appLocale.languageTag?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}

