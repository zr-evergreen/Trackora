package com.evergreen.trackora.feature.addedit

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.ui.components.IconTile
import com.evergreen.trackora.ui.components.JalaliDatePickerDialog
import com.evergreen.trackora.ui.components.TrackoraFilterChip
import com.evergreen.trackora.ui.components.TrackoraFilterRow
import com.evergreen.trackora.ui.components.cardBorder
import com.evergreen.trackora.ui.components.trackoraTextFieldColors
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.text.localizedRelativeDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.TrackoraSpacing
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.ui.theme.container
import com.evergreen.trackora.ui.theme.content
import com.evergreen.trackora.ui.theme.statusColors
import com.evergreen.trackora.util.AppConstants
import com.evergreen.trackora.util.JalaliCalendar
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Screen for adding or editing work entries.
 *
 * ### Layout
 *
 * The form reads top to bottom in the order a job is described — what it is,
 * how many, then its state, day and photo — with the free-text fields first so
 * the keyboard opens once and closes once.
 *
 * Save lives in a bar pinned to the bottom, riding above the keyboard. On a
 * small phone the button at the end of the form was below the fold, and with
 * the keyboard open it was two scrolls away from the field being typed in.
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
        // Copied into app storage rather than stored as-is: the picker's URI
        // is only readable until the app restarts.
        uri?.let { viewModel.onGalleryPhotoPicked(it) }
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val canSave = uiState.isValid && !uiState.isSaving && uiState.hasChanges &&
        !uiState.isImportingPhoto

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            // AutoMirrored: in RTL a back arrow must point
                            // right, or it reads as "forward" to a Persian user.
                            imageVector = TrackoraIcons.Back,
                            contentDescription = stringResource(id = R.string.content_back)
                        )
                    }
                },
                // Flat over the page, tinted once the form scrolls beneath it.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            SaveBar(
                enabled = canSave,
                isSaving = uiState.isSaving,
                onSave = viewModel::save
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // The window is edge to edge, so adjustResize does not shrink
                // it for the keyboard; without this the lower fields (the
                // custom fields, quantity on small phones) sit under the
                // keyboard while being typed into. Shrinking the scroll
                // viewport lets it keep the focused field in view. The save
                // bar already rides on the keyboard and its height is part of
                // paddingValues, so consuming them leaves nothing doubled.
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TrackoraSpacing.screen, vertical = TrackoraSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm)
        ) {
            val fieldShape = MaterialTheme.shapes.medium
            val fieldColors = trackoraTextFieldColors()

            // Title Field
            val titleError = uiState.titleError
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(id = R.string.field_title_required)) },
                placeholder = { Text(stringResource(id = R.string.placeholder_title)) },
                singleLine = true,
                // The field holds user content, so it needs the same bidi
                // treatment as the list that displays it back. Without this a
                // title typed as "Nike order 42:" shows as ":Nike order 42"
                // while being typed, then corrects itself once saved.
                textStyle = LocalTextStyle.current.forUserContent(),
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
                            text = stringResource(
                                id = R.string.char_counter,
                                uiState.title.length,
                                AppConstants.TITLE_MAX_LENGTH
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                shape = fieldShape,
                colors = fieldColors
            )

            // Description Field
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(id = R.string.field_description_optional)) },
                placeholder = { Text(stringResource(id = R.string.placeholder_description)) },
                textStyle = LocalTextStyle.current.forUserContent(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                minLines = 3,
                maxLines = 5,
                supportingText = {
                    Text(
                        text = stringResource(
                            id = R.string.char_counter,
                            uiState.description.length,
                            AppConstants.DESCRIPTION_MAX_LENGTH
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                shape = fieldShape,
                colors = fieldColors
            )

            // Quantity Field
            val quantityError = uiState.quantityError
            OutlinedTextField(
                value = uiState.quantityInput,
                onValueChange = viewModel::onQuantityChange,
                label = { Text(stringResource(id = R.string.field_quantity_optional)) },
                placeholder = { Text(stringResource(id = R.string.placeholder_quantity)) },
                leadingIcon = {
                    Icon(imageVector = TrackoraIcons.Quantity, contentDescription = null)
                },
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
                shape = fieldShape,
                colors = fieldColors
            )

            // Custom Fields - only show if names are defined in settings
            val customFieldNames by viewModel.customFieldNames.collectAsState(
                initial = Triple(
                    "",
                    "",
                    ""
                )
            )

            val customFields = listOf(
                Triple(customFieldNames.first, uiState.customField1, viewModel::onCustomField1Change),
                Triple(customFieldNames.second, uiState.customField2, viewModel::onCustomField2Change),
                Triple(customFieldNames.third, uiState.customField3, viewModel::onCustomField3Change)
            )
            customFields.forEach { (name, value, onChange) ->
                if (name.isNotBlank()) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onChange,
                        label = { Text(name) },
                        placeholder = { Text(stringResource(id = R.string.placeholder_custom_field)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = fieldShape,
                        colors = fieldColors
                    )
                }
            }

            // Status Section
            FormSection(title = stringResource(id = R.string.label_status)) {
                StatusSelector(
                    selected = uiState.status,
                    onSelected = viewModel::onStatusChange
                )
            }

            // Date Section
            FormSection(title = stringResource(id = R.string.label_date)) {
                DateRow(
                    date = uiState.date,
                    onClick = { viewModel.showDatePicker() }
                )
            }

            // Photo Section
            FormSection(title = stringResource(id = R.string.label_photo)) {
                PhotoSelector(
                    photoUri = uiState.photoUri,
                    isImporting = uiState.isImportingPhoto,
                    onTakePhoto = { launchCamera() },
                    onChooseFromGallery = {
                        galleryLauncher.launch("image/*")
                    },
                    onRemovePhoto = {
                        viewModel.clearPhoto()
                    }
                )
            }

            // Error Message
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ErrorNote(message = uiState.errorMessage.orEmpty())
            }

            Spacer(modifier = Modifier.height(TrackoraSpacing.lg))
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
                // UTC both ways; see DatePickerDates for why the device zone
                // moved the date back a day in Iran.
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = DatePickerDates.toPickerMillis(uiState.date)
                )

                DatePickerDialog(
                    onDismissRequest = { viewModel.dismissDatePicker() },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    viewModel.onDateChange(DatePickerDates.fromPickerMillis(millis))
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


/**
 * The pinned bottom bar holding Save.
 *
 * It pads itself for the navigation bar and the keyboard, whichever is
 * taller, so it sits on top of the keyboard while a field is being typed in.
 */
@Composable
private fun SaveBar(
    enabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = TrackoraSpacing.screen, vertical = TrackoraSpacing.md)
        ) {
            Button(
                onClick = onSave,
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = TrackoraIcons.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(TrackoraSpacing.sm))
                Text(
                    text = stringResource(id = R.string.action_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** A labelled group of the form below the text fields. */
@Composable
private fun FormSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TrackoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

/**
 * The three states as chips, each in its own colour and icon once chosen —
 * the same pairing the badge on the saved entry will show.
 */
@Composable
private fun StatusSelector(
    selected: Status,
    onSelected: (Status) -> Unit
) {
    val statusColors = MaterialTheme.statusColors
    TrackoraFilterRow {
        Status.entries.forEach { status ->
            TrackoraFilterChip(
                label = when (status) {
                    Status.IN_PROGRESS -> stringResource(id = R.string.status_in_progress)
                    Status.COMPLETED -> stringResource(id = R.string.status_completed)
                    Status.DELIVERED -> stringResource(id = R.string.status_delivered)
                },
                selected = status == selected,
                onClick = { onSelected(status) },
                icon = TrackoraIcons.forStatus(status),
                selectedContainerColor = statusColors.container(status),
                selectedContentColor = statusColors.content(status)
            )
        }
    }
}

@Composable
private fun DateRow(
    date: LocalDate,
    onClick: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(
                onClickLabel = stringResource(id = R.string.content_select_date),
                role = Role.Button,
                onClick = onClick
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TrackoraSpacing.lg, vertical = TrackoraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = TrackoraIcons.Calendar,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Most entries are logged for the day they happened, so
                    // this reads "امروز" far more often than it spells out a
                    // date — which is how an Iranian user expects it to read.
                    // The exact date is one tap away in the picker.
                    text = localizedRelativeDate(date = date, today = LocalDate.now()),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(id = R.string.hint_date_picker),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = TrackoraIcons.Forward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The photo, or the two ways to add one.
 *
 * While a gallery photo is being copied into the app the area shows progress
 * in its place — Save is held back until the copy lands, and without this the
 * form gave no sign of why.
 */
@Composable
private fun PhotoSelector(
    photoUri: String?,
    isImporting: Boolean,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    val state = when {
        isImporting -> PhotoState.Importing
        photoUri != null -> PhotoState.Present
        else -> PhotoState.Empty
    }
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(TrackoraMotion.MEDIUM)) togetherWith fadeOut(tween(TrackoraMotion.SHORT))
        },
        label = "photo"
    ) { current ->
        when (current) {
            PhotoState.Importing -> Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(TrackoraSpacing.md))
                    Text(
                        text = stringResource(id = R.string.photo_importing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PhotoState.Present -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
            ) {
                AsyncImage(
                    model = photoUri?.let(Uri::parse),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )
                // On a light-backed button so it stays visible over any photo;
                // a bare icon disappeared against pale pictures.
                FilledIconButton(
                    onClick = onRemovePhoto,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(TrackoraSpacing.sm),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = TrackoraIcons.Clear,
                        contentDescription = stringResource(id = R.string.action_remove_photo)
                    )
                }
            }

            PhotoState.Empty -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md)
            ) {
                PhotoSourceTile(
                    icon = TrackoraIcons.Camera,
                    label = stringResource(id = R.string.action_take_photo),
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f)
                )
                PhotoSourceTile(
                    icon = TrackoraIcons.Gallery,
                    label = stringResource(id = R.string.action_choose_from_gallery),
                    onClick = onChooseFromGallery,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private enum class PhotoState { Empty, Importing, Present }

/**
 * One way to add a photo: an icon over its label. Side by side rather than
 * as two full-width buttons, and the label may wrap, so neither the longer
 * Persian label nor large text pushes them off the line.
 */
@Composable
private fun PhotoSourceTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.cardContainer,
        border = cardBorder()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = TrackoraSpacing.md, vertical = TrackoraSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TrackoraSpacing.sm)
        ) {
            IconTile(
                icon = icon,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorNote(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TrackoraSpacing.md),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(TrackoraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(TrackoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = TrackoraIcons.Error, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
