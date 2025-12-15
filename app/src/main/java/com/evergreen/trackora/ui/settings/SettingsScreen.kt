package com.evergreen.trackora.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.R

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = stringResource(id = R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.padding(4.dp))

        LocaleOptionRow(
            label = stringResource(id = R.string.language_system_default),
            selected = uiState.selectedLocale == AppLocale.SYSTEM,
            onSelected = { viewModel.onLocaleSelected(AppLocale.SYSTEM) }
        )
        LocaleOptionRow(
            label = stringResource(id = R.string.language_english),
            selected = uiState.selectedLocale == AppLocale.EN,
            onSelected = { viewModel.onLocaleSelected(AppLocale.EN) }
        )
        LocaleOptionRow(
            label = stringResource(id = R.string.language_persian),
            selected = uiState.selectedLocale == AppLocale.FA,
            onSelected = { viewModel.onLocaleSelected(AppLocale.FA) }
        )
    }
}

@Composable
private fun LocaleOptionRow(
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

