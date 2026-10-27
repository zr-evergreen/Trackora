package com.evergreen.trackora.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.evergreen.trackora.di.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the daily reminder about undelivered work is switched on.
 *
 * Defaults to on, because the reminder is the one thing the app does when it is
 * closed and it is the reason a user notices work sitting undelivered. The
 * switch exists because a reminder the user cannot turn off is a reason to
 * uninstall — and until now there was no way to stop it.
 */
@Singleton
class ReminderPreferences @Inject constructor(
    @SettingsDataStore private val dataStore: DataStore<Preferences>
) {
    private val enabledKey = booleanPreferencesKey("daily_reminder_enabled")

    val enabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[enabledKey] ?: DEFAULT_ENABLED
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[enabledKey] = enabled }
    }

    companion object {
        const val DEFAULT_ENABLED = true
    }
}
