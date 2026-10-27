package com.evergreen.trackora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.R
import com.evergreen.trackora.export.WorkEntryCsv
import com.evergreen.trackora.locale.AppLocale
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.theme.AppThemeMode
import com.evergreen.trackora.ui.components.IconTile
import com.evergreen.trackora.ui.components.ScreenHeader
import com.evergreen.trackora.ui.components.cardBorder
import com.evergreen.trackora.ui.components.trackoraTextFieldColors
import com.evergreen.trackora.ui.text.localizedDigits
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.util.AppVersion

/**
 * App settings, laid out the way Android's own settings are: a quiet label
 * per group, and the group's rows on one card beneath it. Every row is a
 * single target — the whole row selects, toggles or opens — and a single
 * accessibility node, so a screen reader announces "Dark, radio button,
 * selected" once rather than the label and the control separately.
 */

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Column headings come from resources here rather than in the view model,
    // which has no Context and should not be resolving strings.
    val headers = WorkEntryCsv.Headers(
        date = stringResource(R.string.export_column_date),
        title = stringResource(R.string.export_column_title),
        description = stringResource(R.string.export_column_description),
        quantity = stringResource(R.string.export_column_quantity),
        status = stringResource(R.string.export_column_status),
        customField1 = stringResource(R.string.export_column_custom_1),
        customField2 = stringResource(R.string.export_column_custom_2),
        customField3 = stringResource(R.string.export_column_custom_3),
        inProgress = stringResource(R.string.status_in_progress),
        completed = stringResource(R.string.status_completed),
        delivered = stringResource(R.string.status_delivered),
    )
    val chooserTitle = stringResource(R.string.export_chooser_title)
    val nothingToExport = stringResource(R.string.export_nothing)
    val exportFailed = stringResource(R.string.export_failed)

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.ShareCsv ->
                    context.startActivity(viewModel.shareIntent(event.uri, chooserTitle))

                SettingsEvent.NothingToExport ->
                    snackbarHostState.showSnackbar(nothingToExport)

                SettingsEvent.ExportFailed ->
                    snackbarHostState.showSnackbar(exportFailed)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreenContent(
            uiState = uiState,
            onLocaleSelected = viewModel::onLocaleSelected,
            onThemeSelected = viewModel::onThemeSelected,
            onCustomFieldsChanged = viewModel::onCustomFieldsChanged,
            onReminderEnabledChanged = viewModel::onReminderEnabledChanged,
            onExportDataClick = { viewModel.exportCsv(headers) },
            contentPadding = contentPadding
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
        )
    }
}

@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    onLocaleSelected: (AppLocale) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onCustomFieldsChanged: (CustomFields) -> Unit,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onExportDataClick: () -> Unit,
    contentPadding: PaddingValues
) {
    val scrollState = rememberScrollState()

    // Initialize custom fields from state
    var customField1 by rememberSaveable { mutableStateOf(uiState.customFields.field1Name) }
    var customField2 by rememberSaveable { mutableStateOf(uiState.customFields.field2Name) }
    var customField3 by rememberSaveable { mutableStateOf(uiState.customFields.field3Name) }

    // Set once the user types. From then on the fields are the source of
    // truth: each keystroke is saved to DataStore and its echo comes back a
    // moment later, and copying those late echoes into the fields overwrote
    // characters typed in between — fast input saved "Customer" as "Custmero".
    var editedCustomFields by rememberSaveable { mutableStateOf(false) }

    // Picks up the stored names when they load, until the user starts editing.
    LaunchedEffect(uiState.customFields) {
        if (editedCustomFields) return@LaunchedEffect
        customField1 = uiState.customFields.field1Name
        customField2 = uiState.customFields.field2Name
        customField3 = uiState.customFields.field3Name
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge to edge, adjustResize does not shrink the window for the
            // keyboard, so the custom-field inputs were typed into while
            // hidden under it. The full keyboard height is needed here:
            // contentPadding is applied inside the scroll, not to the
            // viewport, so none of it keeps the viewport clear of the keyboard.
            .imePadding()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(bottom = TrackoraSpacing.xxl)
    ) {
        ScreenHeader(title = stringResource(id = R.string.settings_title))

        // Appearance
        SettingsGroup(
            title = stringResource(id = R.string.settings_section_appearance),
            description = stringResource(id = R.string.settings_section_appearance_desc)
        ) {
            SettingsCard {
                OptionGroupLabel(text = stringResource(id = R.string.settings_theme))
                Column(modifier = Modifier.selectableGroup()) {
                    SettingsRadioRow(
                        label = stringResource(id = R.string.theme_system_default),
                        selected = uiState.selectedTheme == AppThemeMode.SYSTEM,
                        onSelected = { onThemeSelected(AppThemeMode.SYSTEM) },
                        leading = { RowIcon(TrackoraIcons.ThemeSystem) }
                    )
                    SettingsRadioRow(
                        label = stringResource(id = R.string.theme_light),
                        selected = uiState.selectedTheme == AppThemeMode.LIGHT,
                        onSelected = { onThemeSelected(AppThemeMode.LIGHT) },
                        leading = { RowIcon(TrackoraIcons.ThemeLight) }
                    )
                    SettingsRadioRow(
                        label = stringResource(id = R.string.theme_dark),
                        selected = uiState.selectedTheme == AppThemeMode.DARK,
                        onSelected = { onThemeSelected(AppThemeMode.DARK) },
                        leading = { RowIcon(TrackoraIcons.ThemeDark) }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = TrackoraSpacing.lg),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                OptionGroupLabel(text = stringResource(id = R.string.settings_language))
                Column(modifier = Modifier.selectableGroup()) {
                    SettingsRadioRow(
                        label = stringResource(id = R.string.language_system_default),
                        selected = uiState.selectedLocale == AppLocale.SYSTEM,
                        onSelected = { onLocaleSelected(AppLocale.SYSTEM) },
                        leading = { RowIcon(TrackoraIcons.Language) }
                    )
                    // Each language is marked in its own script, which is how
                    // someone who cannot read the other one finds theirs.
                    SettingsRadioRow(
                        label = stringResource(id = R.string.language_english),
                        selected = uiState.selectedLocale == AppLocale.EN,
                        onSelected = { onLocaleSelected(AppLocale.EN) },
                        leading = { ScriptMark(text = "En") }
                    )
                    SettingsRadioRow(
                        label = stringResource(id = R.string.language_persian),
                        selected = uiState.selectedLocale == AppLocale.FA,
                        onSelected = { onLocaleSelected(AppLocale.FA) },
                        leading = { ScriptMark(text = "فا") }
                    )
                }
            }
        }

        // Work Fields
        SettingsGroup(
            title = stringResource(id = R.string.settings_section_work_fields),
            description = stringResource(id = R.string.settings_section_work_fields_desc)
        ) {
            val fieldColors = trackoraTextFieldColors()
            Column(verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.xs)) {
                OutlinedTextField(
                    value = customField1,
                    onValueChange = {
                        editedCustomFields = true
                        customField1 = it
                        onCustomFieldsChanged(
                            CustomFields(
                                field1Name = it,
                                field2Name = customField2,
                                field3Name = customField3
                            )
                        )
                    },
                    label = {
                        Text(text = stringResource(id = R.string.settings_custom_field_1))
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.settings_custom_field_1_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = stringResource(id = R.string.settings_custom_field_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = customField2,
                    onValueChange = {
                        editedCustomFields = true
                        customField2 = it
                        onCustomFieldsChanged(
                            CustomFields(
                                field1Name = customField1,
                                field2Name = it,
                                field3Name = customField3
                            )
                        )
                    },
                    label = {
                        Text(text = stringResource(id = R.string.settings_custom_field_2))
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.settings_custom_field_2_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = stringResource(id = R.string.settings_custom_field_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = customField3,
                    onValueChange = {
                        editedCustomFields = true
                        customField3 = it
                        onCustomFieldsChanged(
                            CustomFields(
                                field1Name = customField1,
                                field2Name = customField2,
                                field3Name = it
                            )
                        )
                    },
                    label = {
                        Text(text = stringResource(id = R.string.settings_custom_field_3))
                    },
                    placeholder = {
                        Text(text = stringResource(id = R.string.settings_custom_field_3_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = stringResource(id = R.string.settings_custom_field_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors
                )
            }
        }

        // Reminders
        SettingsGroup(title = stringResource(id = R.string.settings_section_reminders)) {
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The whole row toggles, so the target is the row
                        // height rather than the switch itself.
                        .toggleable(
                            value = uiState.reminderEnabled,
                            role = Role.Switch,
                            onValueChange = onReminderEnabledChanged
                        )
                        .padding(horizontal = TrackoraSpacing.lg, vertical = TrackoraSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RowTile(icon = TrackoraIcons.Reminder)
                    RowText(
                        title = stringResource(id = R.string.settings_reminder_toggle),
                        supporting = stringResource(id = R.string.settings_section_reminders_desc),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.reminderEnabled,
                        onCheckedChange = null
                    )
                }
            }
        }

        // Data
        SettingsGroup(title = stringResource(id = R.string.settings_section_data)) {
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onExportDataClick)
                        .padding(horizontal = TrackoraSpacing.lg, vertical = TrackoraSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RowTile(icon = TrackoraIcons.Export)
                    RowText(
                        title = stringResource(id = R.string.settings_export_data_label),
                        supporting = stringResource(id = R.string.settings_section_data_desc),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = TrackoraIcons.Forward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // About
        SettingsGroup(title = stringResource(id = R.string.settings_section_about)) {
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = TrackoraSpacing.lg, vertical = TrackoraSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RowTile(icon = TrackoraIcons.About)
                    RowText(
                        title = stringResource(id = R.string.app_name),
                        supporting = stringResource(id = R.string.settings_about_version_label) +
                            " " + localizedDigits(AppVersion.displayVersion),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** A labelled block of the settings page: its heading, an optional line of explanation, then its rows. */
@Composable
private fun SettingsGroup(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TrackoraSpacing.screen)
            .padding(top = TrackoraSpacing.lg, bottom = TrackoraSpacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = TrackoraSpacing.xs)
                .semantics { heading() }
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = TrackoraSpacing.xs, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(TrackoraSpacing.sm))
        content()
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Column(modifier = Modifier.padding(vertical = TrackoraSpacing.xs), content = content)
    }
}

/** Names the choice a run of radio rows makes, inside a card. */
@Composable
private fun OptionGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = TrackoraSpacing.lg, end = TrackoraSpacing.lg, top = TrackoraSpacing.md, bottom = TrackoraSpacing.xs)
            .semantics { heading() }
    )
}

@Composable
private fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton
            )
            .padding(start = TrackoraSpacing.lg, end = TrackoraSpacing.xs, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // The row is the control; the button only shows its state.
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(TrackoraSpacing.md)
        )
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
}

/** A language's own short name, in the slot where the theme rows show an icon. */
@Composable
private fun ScriptMark(text: String) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun RowTile(icon: ImageVector) {
    IconTile(
        icon = icon,
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun RowText(
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
