package com.evergreen.trackora.feature.addedit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntryByIdUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.util.AppConstants
import com.evergreen.trackora.util.PersianDigits
import com.evergreen.trackora.util.WorkEntryValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for creating and editing work entries.
 */
@HiltViewModel
class AddEditWorkViewModel @Inject constructor(
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase,
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase,
    private val getWorkEntryByIdUseCase: GetWorkEntryByIdUseCase,
    private val galleryPhotoImporter: GalleryPhotoImporter,
    customFieldsManager: CustomFieldsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val currentEntryId: Long? = savedStateHandle["entryId"]

    private val _uiState = MutableStateFlow(AddEditWorkUiState())
    val uiState: StateFlow<AddEditWorkUiState> = _uiState.asStateFlow()

    private var photoImportJob: Job? = null

    // Custom field names from settings
    val customFieldNames = customFieldsManager.allCustomFields.map { fields: CustomFields ->
        Triple(fields.field1Name, fields.field2Name, fields.field3Name)
    }

    init {
        currentEntryId?.let { id ->
            loadEntry(id)
        }
    }

    fun onTitleChange(value: String) {
        val sanitized = WorkEntryValidator.sanitizeTitle(value)
        val validationResult = WorkEntryValidator.validateTitle(
            sanitized,
            allowEmptyWhileTyping = value.isNotEmpty()
        )
        _uiState.update {
            it.copy(
                title = sanitized,
                titleError = validationResult.errorMessage,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onDescriptionChange(value: String) {
        val sanitized = WorkEntryValidator.sanitizeDescription(value)
        _uiState.update {
            it.copy(
                description = sanitized,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onCustomField1Change(value: String) {
        _uiState.update {
            it.copy(
                customField1 = value,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onCustomField2Change(value: String) {
        _uiState.update {
            it.copy(
                customField2 = value,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onCustomField3Change(value: String) {
        _uiState.update {
            it.copy(
                customField3 = value,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onQuantityChange(value: String) {
        val sanitized = WorkEntryValidator.sanitizeQuantity(value)
        val validationResult = WorkEntryValidator.validateQuantity(sanitized)
        _uiState.update {
            it.copy(
                quantityInput = sanitized,
                quantityError = validationResult.errorMessage,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    fun onStatusChange(status: Status) {
        _uiState.update { it.copy(status = status, isSaved = false) }
    }

    fun onDateChange(date: LocalDate) {
        _uiState.update { it.copy(date = date, showDatePicker = false, isSaved = false) }
    }

    fun showDatePicker() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    fun dismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
    }

    fun onPhotoSelected(uri: String?) {
        _uiState.update {
            it.copy(
                photoUri = uri,
                errorMessage = null,
                isSaved = false
            )
        }
    }

    /**
     * Attaches a photo picked from the gallery by copying it into app storage
     * first. The picker's own URI stops being readable once the app restarts;
     * see [GalleryPhotoImporter].
     */
    fun onGalleryPhotoPicked(source: Uri) {
        // A newer pick replaces a copy still in progress, so the older one
        // finishing late can neither attach its photo nor end the busy state.
        photoImportJob?.cancel()
        _uiState.update { it.copy(isImportingPhoto = true) }
        photoImportJob = viewModelScope.launch {
            val copied = try {
                galleryPhotoImporter.import(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Same outcome as a failed copy: nothing to attach. Escaping
                // the coroutine would crash the app instead.
                null
            }
            if (copied != null) {
                onPhotoSelected(copied)
            } else {
                _uiState.update {
                    it.copy(errorMessage = AppConstants.Errors.FAILED_TO_ATTACH_PHOTO)
                }
            }
            _uiState.update { it.copy(isImportingPhoto = false) }
        }
    }

    /**
     * Abandons a gallery copy still in progress and leaves the photo as it was
     * before the pick. A copy from a slow or offline provider can take as long
     * as the provider likes; this frees Save without throwing away the form.
     */
    fun cancelPhotoImport() {
        photoImportJob?.cancel()
        photoImportJob = null
        _uiState.update { it.copy(isImportingPhoto = false) }
    }

    /**
     * Attaches a photo just taken with the camera. A gallery copy still in
     * progress is abandoned first, or it would replace this photo when it
     * finished.
     */
    fun onCameraPhotoTaken(uri: String) {
        cancelPhotoImport()
        onPhotoSelected(uri)
    }

    fun clearPhoto() {
        _uiState.update {
            it.copy(
                photoUri = null,
                isSaved = false
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadEntry(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val entry = getWorkEntryByIdUseCase(id)
                if (entry != null) {
                    _uiState.update {
                        it.copy(
                            title = entry.title,
                            description = entry.description ?: "",
                            quantityInput = entry.quantity?.toString() ?: "",
                            customField1 = entry.customField1 ?: "",
                            customField2 = entry.customField2 ?: "",
                            customField3 = entry.customField3 ?: "",
                            status = entry.status,
                            date = entry.date,
                            photoUri = entry.photoUri,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = AppConstants.Errors.ENTRY_NOT_FOUND
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_LOAD
                    )
                }
            }
        }
    }

    fun save() {
        val currentState = _uiState.value

        // Saving before a picked photo has been copied would store the entry
        // without it, silently. The Save button is disabled meanwhile; this
        // is the backstop.
        if (currentState.isImportingPhoto) return

        // Validate all fields using centralized validator
        val (titleValidation, quantityValidation) = WorkEntryValidator.validateForSave(
            currentState.title,
            currentState.quantityInput
        )

        if (!titleValidation.isValid || !quantityValidation.isValid) {
            _uiState.update {
                it.copy(
                    titleError = titleValidation.errorMessage,
                    quantityError = quantityValidation.errorMessage,
                    errorMessage = titleValidation.errorMessage ?: quantityValidation.errorMessage
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    titleError = null,
                    quantityError = null
                )
            }
            try {
                // The field preserves whichever digit system the user typed in,
                // so normalise here: the database column must hold a number,
                // not a script-dependent rendering of one.
                val quantity = PersianDigits.toWestern(currentState.quantityInput).toIntOrNull()
                val description = currentState.description.trim().takeIf { it.isNotBlank() }
                val customField1 = currentState.customField1.trim().takeIf { it.isNotBlank() }
                val customField2 = currentState.customField2.trim().takeIf { it.isNotBlank() }
                val customField3 = currentState.customField3.trim().takeIf { it.isNotBlank() }
                val entry = WorkEntry(
                    id = currentEntryId ?: 0,
                    title = currentState.title.trim(),
                    description = description,
                    quantity = quantity,
                    status = currentState.status,
                    date = currentState.date,
                    customField1 = customField1,
                    customField2 = customField2,
                    customField3 = customField3,
                    photoUri = currentState.photoUri?.takeIf { it.isNotBlank() }
                )

                if (currentEntryId == null) {
                    insertWorkEntryUseCase(entry)
                } else {
                    updateWorkEntryUseCase(entry)
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: AppConstants.Errors.FAILED_TO_SAVE
                    )
                }
            }
        }
    }
}
