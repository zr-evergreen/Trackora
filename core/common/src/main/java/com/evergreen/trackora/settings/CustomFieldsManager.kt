package com.evergreen.trackora.settings

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

    val customField1Name: Flow<String> = dataStore.data.map { prefs ->
        prefs[customField1Key] ?: ""
    }

    val customField2Name: Flow<String> = dataStore.data.map { prefs ->
        prefs[customField2Key] ?: ""
    }

    val customField3Name: Flow<String> = dataStore.data.map { prefs ->
        prefs[customField3Key] ?: ""
    }

    val allCustomFields: Flow<CustomFields> = dataStore.data.map { prefs ->
        CustomFields(
            field1Name = prefs[customField1Key] ?: "",
            field2Name = prefs[customField2Key] ?: "",
            field3Name = prefs[customField3Key] ?: ""
        )
    }

    suspend fun setCustomField1Name(name: String) {
        dataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(customField1Key)
            } else {
                prefs[customField1Key] = name.trim()
            }
        }
    }

    suspend fun setCustomField2Name(name: String) {
        dataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(customField2Key)
            } else {
                prefs[customField2Key] = name.trim()
            }
        }
    }

    suspend fun setCustomField3Name(name: String) {
        dataStore.edit { prefs ->
            if (name.isBlank()) {
                prefs.remove(customField3Key)
            } else {
                prefs[customField3Key] = name.trim()
            }
        }
    }

    suspend fun setCustomFields(fields: CustomFields) {
        dataStore.edit { prefs ->
            if (fields.field1Name.isBlank()) {
                prefs.remove(customField1Key)
            } else {
                prefs[customField1Key] = fields.field1Name.trim()
            }

            if (fields.field2Name.isBlank()) {
                prefs.remove(customField2Key)
            } else {
                prefs[customField2Key] = fields.field2Name.trim()
            }

            if (fields.field3Name.isBlank()) {
                prefs.remove(customField3Key)
            } else {
                prefs[customField3Key] = fields.field3Name.trim()
            }
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

