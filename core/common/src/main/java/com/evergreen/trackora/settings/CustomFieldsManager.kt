package com.evergreen.trackora.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages custom field names for work entries.
 * Persists user-defined labels for customField1, customField2, and customField3.
 */
@Singleton
class CustomFieldsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val customField1Key = stringPreferencesKey("custom_field_1_name")
    private val customField2Key = stringPreferencesKey("custom_field_2_name")
    private val customField3Key = stringPreferencesKey("custom_field_3_name")

    /**
     * Flow of all custom field names combined.
     * This is the primary way to observe custom field names.
     */
    val allCustomFields: Flow<CustomFields> = dataStore.data.map { prefs ->
        CustomFields(
            field1Name = prefs[customField1Key] ?: "",
            field2Name = prefs[customField2Key] ?: "",
            field3Name = prefs[customField3Key] ?: ""
        )
    }

    /**
     * Sets a custom field name. If blank, removes the preference.
     * Extracted to reduce code duplication (DRY principle).
     */
    private suspend fun setCustomFieldName(
        key: Preferences.Key<String>,
        name: String
    ) {
        dataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = name.trim()
            }
        }
    }

    suspend fun setCustomField1Name(name: String) {
        setCustomFieldName(customField1Key, name)
    }

    suspend fun setCustomField2Name(name: String) {
        setCustomFieldName(customField2Key, name)
    }

    suspend fun setCustomField3Name(name: String) {
        setCustomFieldName(customField3Key, name)
    }

    suspend fun setCustomFields(fields: CustomFields) {
        dataStore.edit { prefs ->
            setCustomFieldInPreferences(prefs, customField1Key, fields.field1Name)
            setCustomFieldInPreferences(prefs, customField2Key, fields.field2Name)
            setCustomFieldInPreferences(prefs, customField3Key, fields.field3Name)
        }
    }

    /**
     * Helper method to set or remove a custom field in preferences.
     */
    private fun setCustomFieldInPreferences(
        prefs: MutablePreferences,
        key: Preferences.Key<String>,
        name: String
    ) {
        if (name.isBlank()) {
            prefs.remove(key)
        } else {
            prefs[key] = name.trim()
        }
    }

    suspend fun getCustomFields(): CustomFields {
        val prefs = dataStore.data.firstOrNull() ?: return CustomFields()
        return CustomFields(
            field1Name = prefs[customField1Key] ?: "",
            field2Name = prefs[customField2Key] ?: "",
            field3Name = prefs[customField3Key] ?: ""
        )
    }
}

/**
 * Data class representing the three custom field names.
 */
data class CustomFields(
    val field1Name: String = "",
    val field2Name: String = "",
    val field3Name: String = ""
)

