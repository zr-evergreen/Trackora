package com.evergreen.trackora.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.R
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.theme.AppThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onExportDataClick: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreenContent(
        uiState = uiState,
        onLocaleSelected = viewModel::onLocaleSelected,
        onThemeSelected = viewModel::onThemeSelected,
        onExportDataClick = onExportDataClick,
        contentPadding = contentPadding
    )
}

@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    onLocaleSelected: (AppLocale) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onExportDataClick: () -> Unit,
    contentPadding: PaddingValues
) {
    val scrollState = rememberScrollState()

    var customField1 by rememberSaveable { mutableStateOf("") }
    var customField2 by rememberSaveable { mutableStateOf("") }
    var customField3 by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )

        // Appearance
        SettingsSection(
            title = stringResource(id = R.string.settings_section_appearance),
            description = stringResource(id = R.string.settings_section_appearance_desc)
        ) {
            Text(
                text = stringResource(id = R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsRadioRow(
                label = stringResource(id = R.string.theme_system_default),
                selected = uiState.selectedTheme == AppThemeMode.SYSTEM,
                onSelected = { onThemeSelected(AppThemeMode.SYSTEM) }
            )
            SettingsRadioRow(
                label = stringResource(id = R.string.theme_light),
                selected = uiState.selectedTheme == AppThemeMode.LIGHT,
                onSelected = { onThemeSelected(AppThemeMode.LIGHT) }
            )
            SettingsRadioRow(
                label = stringResource(id = R.string.theme_dark),
                selected = uiState.selectedTheme == AppThemeMode.DARK,
                onSelected = { onThemeSelected(AppThemeMode.DARK) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(id = R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsRadioRow(
                label = stringResource(id = R.string.language_system_default),
                selected = uiState.selectedLocale == AppLocale.SYSTEM,
                onSelected = { onLocaleSelected(AppLocale.SYSTEM) }
            )
            SettingsRadioRow(
                label = stringResource(id = R.string.language_english),
                selected = uiState.selectedLocale == AppLocale.EN,
                onSelected = { onLocaleSelected(AppLocale.EN) }
            )
            SettingsRadioRow(
                label = stringResource(id = R.string.language_persian),
                selected = uiState.selectedLocale == AppLocale.FA,
                onSelected = { onLocaleSelected(AppLocale.FA) }
            )
        }

        // Work Fields
        SettingsSection(
            title = stringResource(id = R.string.settings_section_work_fields),
            description = stringResource(id = R.string.settings_section_work_fields_desc)
        ) {
            OutlinedTextField(
                value = customField1,
                onValueChange = { customField1 = it },
                label = {
                    Text(text = stringResource(id = R.string.settings_custom_field_1))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customField2,
                onValueChange = { customField2 = it },
                label = {
                    Text(text = stringResource(id = R.string.settings_custom_field_2))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = customField3,
                onValueChange = { customField3 = it },
                label = {
                    Text(text = stringResource(id = R.string.settings_custom_field_3))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Data
        SettingsSection(
            title = stringResource(id = R.string.settings_section_data),
            description = stringResource(id = R.string.settings_section_data_desc)
        ) {
            Button(
                onClick = onExportDataClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.settings_export_data_label),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // About
        SettingsSection(
            title = stringResource(id = R.string.settings_section_about),
            description = stringResource(id = R.string.settings_section_about_desc)
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.settings_about_version_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected
        )
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}


