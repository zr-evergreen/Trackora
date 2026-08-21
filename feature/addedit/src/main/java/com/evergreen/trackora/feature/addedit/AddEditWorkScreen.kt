package com.evergreen.trackora.feature.addedit

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.components.JalaliDatePickerDialog
import com.evergreen.trackora.ui.text.localizedRelativeDate
import com.evergreen.trackora.util.JalaliCalendar
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Screen for adding or editing work entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditWorkScreen(
    entryId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditWorkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val currentLocale = configuration.locales[0]
    val isPersian = currentLocale.language == "fa"

    // Photo selection state
    var cameraImageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            viewModel.onPhotoSelected(cameraImageUri.toString())
        }
    }

    // Internal function to create photo file and launch camera
    fun launchCameraInternal() {
        val photoFile = File(
            context.getExternalFilesDir(null),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        )
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch camera
            launchCameraInternal()
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onPhotoSelected(it.toString())
        }
    }

    // Public function to launch camera (with permission check)
    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, launch camera
                launchCameraInternal()
            }

            else -> {
                // Request permission
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    val titleText = if (entryId == null) {
        stringResource(id = R.string.add_work_title)
    } else {
        stringResource(id = R.string.edit_work_title)
    }

    if (uiState.isSaved) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            // AutoMirrored: in RTL a back arrow must point
                            // right, or it reads as "forward" to a Persian user.
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.content_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Field
            val titleError = uiState.titleError
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(id = R.string.field_title_required)) },
                placeholder = { Text(stringResource(id = R.string.placeholder_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = titleError != null,
                supportingText = {
                    if (titleError != null) {
                        Text(
                            text = titleError,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "${uiState.title.length}/100",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Description Field
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(id = R.string.field_description_optional)) },
                placeholder = { Text(stringResource(id = R.string.placeholder_description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                supportingText = {
                    Text(
                        text = "${uiState.description.length}/500",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Quantity Field
            val quantityError = uiState.quantityError
            OutlinedTextField(
                value = uiState.quantityInput,
                onValueChange = viewModel::onQuantityChange,
                label = { Text(stringResource(id = R.string.field_quantity_optional)) },
                placeholder = { Text(stringResource(id = R.string.placeholder_quantity)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = quantityError != null,
                supportingText = {
                    if (quantityError != null) {
                        Text(
                            text = quantityError,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (uiState.quantityInput.isNotEmpty()) {
                        Text(
                            text = stringResource(id = R.string.hint_quantity),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Custom Fields - only show if names are defined in settings
            val customFieldNames by viewModel.customFieldNames.collectAsState(
                initial = Triple(
                    "",
                    "",
                    ""
                )
            )

            if (customFieldNames.first.isNotBlank()) {
                OutlinedTextField(
                    value = uiState.customField1,
                    onValueChange = viewModel::onCustomField1Change,
                    label = { Text(customFieldNames.first) },
                    placeholder = { Text(stringResource(id = R.string.placeholder_custom_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            if (customFieldNames.second.isNotBlank()) {
                OutlinedTextField(
                    value = uiState.customField2,
                    onValueChange = viewModel::onCustomField2Change,
                    label = { Text(customFieldNames.second) },
                    placeholder = { Text(stringResource(id = R.string.placeholder_custom_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            if (customFieldNames.third.isNotBlank()) {
                OutlinedTextField(
                    value = uiState.customField3,
                    onValueChange = viewModel::onCustomField3Change,
                    label = { Text(customFieldNames.third) },
                    placeholder = { Text(stringResource(id = R.string.placeholder_custom_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Photo Section
            Text(
                text = stringResource(id = R.string.label_photo),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            PhotoSelector(
                photoUri = uiState.photoUri,
                onTakePhoto = { launchCamera() },
                onChooseFromGallery = {
                    galleryLauncher.launch("image/*")
                },
                onRemovePhoto = {
                    viewModel.clearPhoto()
                }
            )

            // Status Section
            Text(
                text = stringResource(id = R.string.label_status),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            StatusSelector(
                selected = uiState.status,
                onSelected = viewModel::onStatusChange
            )

            // Date Picker Section
            Text(
                text = stringResource(id = R.string.label_date),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            DatePickerCard(
                date = uiState.date,
                onClick = { viewModel.showDatePicker() }
            )

            // Error Message
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = viewModel::save,
                enabled = uiState.isValid && !uiState.isSaving && uiState.hasChanges,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(id = R.string.action_save))
            }
        }

        // Date Picker Dialog
        if (uiState.showDatePicker) {
            if (isPersian) {
                // Use Jalali date picker for Persian
                val initialJalaliDate = JalaliCalendar.gregorianToJalali(uiState.date)
                JalaliDatePickerDialog(
                    initialDate = initialJalaliDate,
                    onDateSelected = { jalaliDate ->
                        val gregorianDate = JalaliCalendar.jalaliToGregorian(jalaliDate)
                        viewModel.onDateChange(gregorianDate)
                    },
                    onDismiss = { viewModel.dismissDatePicker() }
                )
            } else {
                // Use standard Gregorian date picker
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = uiState.date
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )

                DatePickerDialog(
                    onDismissRequest = { viewModel.dismissDatePicker() },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val selectedDate = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    viewModel.onDateChange(selectedDate)
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.action_select))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissDatePicker() }) {
                            Text(stringResource(id = R.string.action_cancel))
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }
    }
}

@Composable
private fun StatusSelector(
    selected: Status,
    onSelected: (Status) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Status.values().forEach { status ->
            FilterChip(
                selected = status == selected,
                onClick = { onSelected(status) },
                label = {
                    Text(
                        text = when (status) {
                            Status.IN_PROGRESS -> stringResource(id = R.string.status_in_progress)
                            Status.COMPLETED -> stringResource(id = R.string.status_completed)
                            Status.DELIVERED -> stringResource(id = R.string.status_delivered)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.height(40.dp)
            )
        }
    }
}

@Composable
private fun DatePickerCard(
    date: LocalDate,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    // Most entries are logged for the day they happened, so
                    // this reads "امروز" far more often than it spells out a
                    // date — which is how an Iranian user expects it to read.
                    // The exact date is one tap away in the picker.
                    text = localizedRelativeDate(date = date, today = LocalDate.now()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.hint_date_picker),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = stringResource(id = R.string.content_select_date),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PhotoSelector(
    photoUri: String?,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    if (photoUri != null) {
        // Show selected photo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box {
                AsyncImage(
                    model = Uri.parse(photoUri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onRemovePhoto,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(id = R.string.action_remove_photo),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        // Show photo selection options - always stack vertically for better UX
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Take Photo Button
            Button(
                onClick = onTakePhoto,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.action_take_photo),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Choose from Gallery Button
            Button(
                onClick = onChooseFromGallery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.action_choose_from_gallery),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}