package com.evergreen.trackora.feature.addedit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntryByIdUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import com.evergreen.trackora.settings.CustomFields
import com.evergreen.trackora.settings.CustomFieldsManager
import com.evergreen.trackora.util.AppConstants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for [AddEditWorkViewModel].
 *
 * This ViewModel carries the app's only write path, and it is doing two jobs at
 * once: creating a new entry and editing an existing one, distinguished purely
 * by whether `entryId` is present in the [SavedStateHandle]. Most of what is
 * worth testing here lives on that seam — an edit that inserts instead of
 * updating would silently duplicate the user's work, which is why
 * [save routes to update and keeps the id when editing] pins the id down rather
 * than just checking that some save happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditWorkViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val insertWorkEntryUseCase = mockk<InsertWorkEntryUseCase>(relaxed = true)
    private val updateWorkEntryUseCase = mockk<UpdateWorkEntryUseCase>(relaxed = true)
    private val getWorkEntryByIdUseCase = mockk<GetWorkEntryByIdUseCase>()
    private val customFieldsManager = mockk<CustomFieldsManager>()

    private val existingEntry = WorkEntry(
        id = 12L,
        title = "Shorten sleeves",
        description = "Navy blazer",
        quantity = 2,
        status = Status.COMPLETED,
        date = LocalDate.of(2026, 4, 9),
        customField1 = "Order 91",
        customField2 = "Mr. Karimi",
        customField3 = "Paid",
        photoUri = "content://photo/9"
    )

    private fun viewModel(entryId: Long? = null): AddEditWorkViewModel {
        every { customFieldsManager.allCustomFields } returns flowOf(
            CustomFields("Order", "Customer", "Payment")
        )
        val savedStateHandle = SavedStateHandle(
            if (entryId == null) emptyMap() else mapOf("entryId" to entryId)
        )
        return AddEditWorkViewModel(
            insertWorkEntryUseCase = insertWorkEntryUseCase,
            updateWorkEntryUseCase = updateWorkEntryUseCase,
            getWorkEntryByIdUseCase = getWorkEntryByIdUseCase,
            customFieldsManager = customFieldsManager,
            savedStateHandle = savedStateHandle
        )
    }

    // --- Create mode -------------------------------------------------------

    @Test
    fun `starts blank and does not load anything in create mode`() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.title)
            assertEquals("", state.description)
            assertEquals("", state.quantityInput)
            assertEquals(Status.IN_PROGRESS, state.status)
            assertFalse(state.isLoading)
            assertFalse(state.isSaved)
            assertNull(state.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { getWorkEntryByIdUseCase(any()) }
    }

    @Test
    fun `save routes to insert with a zero id when creating`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Cut lining")
        vm.onQuantityChange("4")
        vm.onStatusChange(Status.DELIVERED)
        vm.onDateChange(LocalDate.of(2026, 6, 1))

        val entry = slot<WorkEntry>()
        vm.save()

        coVerify(exactly = 1) { insertWorkEntryUseCase(capture(entry)) }
        coVerify(exactly = 0) { updateWorkEntryUseCase(any()) }
        assertEquals(0L, entry.captured.id)
        assertEquals("Cut lining", entry.captured.title)
        assertEquals(4, entry.captured.quantity)
        assertEquals(Status.DELIVERED, entry.captured.status)
        assertEquals(LocalDate.of(2026, 6, 1), entry.captured.date)
        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isSaving)
    }

    @Test
    fun `blank optional fields are saved as null rather than empty strings`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Press seams")
        // Whitespace only — the user tabbed through without typing anything.
        vm.onDescriptionChange("   ")
        vm.onCustomField1Change("  ")
        vm.onCustomField2Change("")

        val entry = slot<WorkEntry>()
        vm.save()

        coVerify { insertWorkEntryUseCase(capture(entry)) }
        // Storing "" here would make the field render as set-but-empty on the
        // way back out, so the distinction matters downstream.
        assertNull(entry.captured.description)
        assertNull(entry.captured.customField1)
        assertNull(entry.captured.customField2)
        assertNull(entry.captured.quantity)
    }

    @Test
    fun `title is trimmed before it reaches the use case`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Fit hem   ")

        val entry = slot<WorkEntry>()
        vm.save()

        coVerify { insertWorkEntryUseCase(capture(entry)) }
        assertEquals("Fit hem", entry.captured.title)
    }

    // --- Validation --------------------------------------------------------

    @Test
    fun `save with a blank title sets an error and never calls the use case`() = runTest {
        val vm = viewModel()

        vm.save()

        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
        coVerify(exactly = 0) { updateWorkEntryUseCase(any()) }
        val state = vm.uiState.value
        assertEquals(AppConstants.Errors.TITLE_REQUIRED, state.titleError)
        assertEquals(AppConstants.Errors.TITLE_REQUIRED, state.errorMessage)
        assertFalse(state.isSaved)
        assertFalse(state.isSaving)
    }

    @Test
    fun `save with a whitespace only title is rejected`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("    ")

        vm.save()

        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
        assertEquals(AppConstants.Errors.TITLE_REQUIRED, vm.uiState.value.titleError)
    }

    @Test
    fun `typing does not show a required error until save is attempted`() = runTest {
        val vm = viewModel()

        // Typing then clearing the field should not scold the user mid-edit.
        vm.onTitleChange("A")
        assertNull(vm.uiState.value.titleError)
        vm.onTitleChange("")

        assertEquals(AppConstants.Errors.TITLE_REQUIRED, vm.uiState.value.titleError)
        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
    }

    @Test
    fun `quantity over the maximum blocks the save`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("Bulk order")
        vm.onQuantityChange("${AppConstants.QUANTITY_MAX_VALUE + 1}")

        vm.save()

        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
        assertEquals(AppConstants.Errors.QUANTITY_TOO_LARGE, vm.uiState.value.quantityError)
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun `non digit quantity input is stripped as it is typed`() = runTest {
        val vm = viewModel()

        vm.onQuantityChange("1a2b3")

        assertEquals("123", vm.uiState.value.quantityInput)
        assertNull(vm.uiState.value.quantityError)
    }

    @Test
    fun `title is capped at the maximum length as it is typed`() = runTest {
        val vm = viewModel()

        vm.onTitleChange("x".repeat(AppConstants.TITLE_MAX_LENGTH + 50))

        assertEquals(AppConstants.TITLE_MAX_LENGTH, vm.uiState.value.title.length)
        assertNull(vm.uiState.value.titleError)
    }

    // --- Edit mode ---------------------------------------------------------

    @Test
    fun `existing entry is loaded into the form when an id is supplied`() = runTest {
        coEvery { getWorkEntryByIdUseCase(12L) } returns existingEntry

        val state = viewModel(entryId = 12L).uiState.value

        assertEquals("Shorten sleeves", state.title)
        assertEquals("Navy blazer", state.description)
        assertEquals("2", state.quantityInput)
        assertEquals(Status.COMPLETED, state.status)
        assertEquals(LocalDate.of(2026, 4, 9), state.date)
        assertEquals("Order 91", state.customField1)
        assertEquals("content://photo/9", state.photoUri)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `null fields on the loaded entry become empty strings in the form`() = runTest {
        coEvery { getWorkEntryByIdUseCase(3L) } returns WorkEntry(
            id = 3L,
            title = "Minimal",
            status = Status.IN_PROGRESS,
            date = LocalDate.of(2026, 2, 2)
        )

        val state = viewModel(entryId = 3L).uiState.value

        // Text fields cannot hold null, so the ViewModel must substitute "".
        assertEquals("", state.description)
        assertEquals("", state.quantityInput)
        assertEquals("", state.customField1)
        assertNull(state.photoUri)
    }

    @Test
    fun `save routes to update and keeps the id when editing`() = runTest {
        coEvery { getWorkEntryByIdUseCase(12L) } returns existingEntry
        val vm = viewModel(entryId = 12L)
        vm.onTitleChange("Shorten sleeves twice")

        val entry = slot<WorkEntry>()
        vm.save()

        coVerify(exactly = 1) { updateWorkEntryUseCase(capture(entry)) }
        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
        // Losing the id here would insert a duplicate instead of editing.
        assertEquals(12L, entry.captured.id)
        assertEquals("Shorten sleeves twice", entry.captured.title)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `a missing entry surfaces a not found error`() = runTest {
        coEvery { getWorkEntryByIdUseCase(99L) } returns null

        val state = viewModel(entryId = 99L).uiState.value

        assertEquals(AppConstants.Errors.ENTRY_NOT_FOUND, state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failure while loading clears the loading flag and reports the message`() = runTest {
        coEvery { getWorkEntryByIdUseCase(5L) } throws RuntimeException("db unavailable")

        val state = viewModel(entryId = 5L).uiState.value

        assertEquals("db unavailable", state.errorMessage)
        assertFalse(state.isLoading)
    }

    // --- Save failures -----------------------------------------------------

    @Test
    fun `a failure while saving clears the saving flag and leaves isSaved false`() = runTest {
        coEvery { insertWorkEntryUseCase(any()) } throws RuntimeException("disk full")
        val vm = viewModel()
        vm.onTitleChange("Anything")

        vm.save()

        val state = vm.uiState.value
        assertEquals("disk full", state.errorMessage)
        assertFalse(state.isSaving)
        // isSaved drives navigation away from the screen; if this were true the
        // user would be sent back believing their entry was stored.
        assertFalse(state.isSaved)
    }

    @Test
    fun `a save failure without a message falls back to the default text`() = runTest {
        coEvery { insertWorkEntryUseCase(any()) } throws RuntimeException()
        val vm = viewModel()
        vm.onTitleChange("Anything")

        vm.save()

        assertEquals(AppConstants.Errors.FAILED_TO_SAVE, vm.uiState.value.errorMessage)
    }

    @Test
    fun `clearError removes the message without touching the rest of the form`() = runTest {
        coEvery { insertWorkEntryUseCase(any()) } throws RuntimeException("nope")
        val vm = viewModel()
        vm.onTitleChange("Keep me")
        vm.save()

        vm.clearError()

        assertNull(vm.uiState.value.errorMessage)
        assertEquals("Keep me", vm.uiState.value.title)
    }

    // --- Incidental state --------------------------------------------------

    @Test
    fun `editing after a successful save clears the saved flag`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("First")
        vm.save()
        assertTrue(vm.uiState.value.isSaved)

        vm.onTitleChange("Second")

        // Otherwise the screen would navigate away the moment the user resumed
        // typing on a form it had already saved once.
        assertFalse(vm.uiState.value.isSaved)
    }

    @Test
    fun `date picker visibility toggles and closes on selection`() = runTest {
        val vm = viewModel()

        vm.showDatePicker()
        assertTrue(vm.uiState.value.showDatePicker)

        vm.dismissDatePicker()
        assertFalse(vm.uiState.value.showDatePicker)

        vm.showDatePicker()
        vm.onDateChange(LocalDate.of(2026, 7, 7))
        assertFalse(vm.uiState.value.showDatePicker)
        assertEquals(LocalDate.of(2026, 7, 7), vm.uiState.value.date)
    }

    @Test
    fun `clearing a photo removes it from the state and from the saved entry`() = runTest {
        val vm = viewModel()
        vm.onTitleChange("With photo")
        vm.onPhotoSelected("content://photo/1")
        assertEquals("content://photo/1", vm.uiState.value.photoUri)

        vm.clearPhoto()
        val entry = slot<WorkEntry>()
        vm.save()

        coVerify { insertWorkEntryUseCase(capture(entry)) }
        assertNull(entry.captured.photoUri)
    }

    @Test
    fun `custom field names are exposed from settings`() = runTest {
        val vm = viewModel()

        vm.customFieldNames.test {
            assertEquals(Triple("Order", "Customer", "Payment"), awaitItem())
            awaitComplete()
        }
    }
}
