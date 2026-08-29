package com.evergreen.trackora.feature.today

import app.cash.turbine.test
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetUndeliveredWorkUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateUseCase
import com.evergreen.trackora.domain.usecase.InsertWorkEntryUseCase
import com.evergreen.trackora.domain.usecase.UpdateWorkEntryUseCase
import com.evergreen.trackora.util.AppConstants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getWorkEntriesByDateUseCase: GetWorkEntriesByDateUseCase = mockk()
    private val getUndeliveredWorkUseCase: GetUndeliveredWorkUseCase = mockk {
        every { this@mockk.invoke() } returns flowOf(emptyList())
    }
    private val insertWorkEntryUseCase: InsertWorkEntryUseCase = mockk(relaxed = true)
    private val updateWorkEntryUseCase: UpdateWorkEntryUseCase = mockk(relaxed = true)

    private val today: LocalDate = LocalDate.now()

    private fun entry(
        id: Long,
        title: String = "Entry $id",
        status: Status = Status.IN_PROGRESS
    ) = WorkEntry(id = id, title = title, status = status, date = today)

    private fun viewModel() = TodayViewModel(
        getWorkEntriesByDateUseCase,
        getUndeliveredWorkUseCase,
        insertWorkEntryUseCase,
        updateWorkEntryUseCase
    )

    // --- Loading and initial state -----------------------------------------

    @Test
    fun `entries emitted by the use case land in state with loading cleared`() = runTest {
        val entries = listOf(entry(1), entry(2, status = Status.COMPLETED))
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(entries)

        viewModel().uiState.test {
            val state = awaitItem()

            assertEquals(entries, state.todayEntries)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `state stays loading until the first emission arrives`() = runTest {
        // A SharedFlow with no replay never produces a value on subscription,
        // so collection suspends and the intermediate loading state is
        // observable. A StandardTestDispatcher is needed here because the
        // unconfined default would run the init block to completion before
        // the constructor returned, hiding the transition entirely.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entries = MutableSharedFlow<List<WorkEntry>>()
        every { getWorkEntriesByDateUseCase(any()) } returns entries

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue("loading while the flow is still silent", viewModel.uiState.value.isLoading)

        entries.emit(listOf(entry(1)))
        advanceUntilIdle()

        assertFalse("loading cleared once data arrives", viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.todayEntries.size)
    }

    @Test
    fun `the use case is queried for today's date`() = runTest {
        val requestedDate = slot<LocalDate>()
        every { getWorkEntriesByDateUseCase(capture(requestedDate)) } returns flowOf(emptyList())

        viewModel()

        assertEquals(LocalDate.now(), requestedDate.captured)
    }

    @Test
    fun `an empty result produces the empty state`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())

        viewModel().uiState.test {
            val state = awaitItem()

            assertTrue(state.isEmpty)
            assertEquals(0, state.todayEntries.size)
        }
    }

    // --- Error path ---------------------------------------------------------

    @Test
    fun `a failing flow sets the error message and clears loading`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns
            flow { throw RuntimeException("database unavailable") }

        viewModel().uiState.test {
            val state = awaitItem()

            assertEquals("database unavailable", state.errorMessage)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `a failure without a message falls back to the generic error`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flow { throw RuntimeException() }

        viewModel().uiState.test {
            assertEquals(
                AppConstants.Errors.FAILED_TO_LOAD_ENTRIES,
                awaitItem().errorMessage
            )
        }
    }

    @Test
    fun `clearError removes the error message`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flow { throw RuntimeException("boom") }
        val viewModel = viewModel()
        assertEquals("boom", viewModel.uiState.value.errorMessage)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.errorMessage)
    }

    // --- Validation ---------------------------------------------------------

    @Test
    fun `a blank title is rejected and never reaches the insert use case`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        val viewModel = viewModel()

        viewModel.addWorkEntry(title = "   ")

        assertEquals(
            AppConstants.Errors.TITLE_CANNOT_BE_EMPTY,
            viewModel.uiState.value.errorMessage
        )
        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
    }

    @Test
    fun `an empty title is rejected and never reaches the insert use case`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        val viewModel = viewModel()

        viewModel.addWorkEntry(title = "")

        assertEquals(
            AppConstants.Errors.TITLE_CANNOT_BE_EMPTY,
            viewModel.uiState.value.errorMessage
        )
        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
    }

    // --- Insert -------------------------------------------------------------

    @Test
    fun `a valid entry is inserted with today's date and trimmed text`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        val inserted = slot<WorkEntry>()
        coEvery { insertWorkEntryUseCase(capture(inserted)) } returns 1L

        viewModel().addWorkEntry(
            title = "  Hem trousers  ",
            description = "  charcoal  ",
            quantity = 3,
            status = Status.IN_PROGRESS
        )

        assertEquals("Hem trousers", inserted.captured.title)
        assertEquals("charcoal", inserted.captured.description)
        assertEquals(3, inserted.captured.quantity)
        assertEquals(Status.IN_PROGRESS, inserted.captured.status)
        assertEquals(LocalDate.now(), inserted.captured.date)
    }

    @Test
    fun `a failing insert surfaces its message as the error`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        coEvery { insertWorkEntryUseCase(any()) } throws RuntimeException("disk full")
        val viewModel = viewModel()

        viewModel.addWorkEntry(title = "Valid title")

        assertEquals("disk full", viewModel.uiState.value.errorMessage)
    }

    // --- Status updates -----------------------------------------------------

    @Test
    fun `updating the status of a loaded entry passes the new status through`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry(1)))
        val updated = slot<WorkEntry>()
        coEvery { updateWorkEntryUseCase(capture(updated)) } returns Unit

        viewModel().updateEntryStatus(entryId = 1L, newStatus = Status.COMPLETED)

        assertEquals(1L, updated.captured.id)
        assertEquals(Status.COMPLETED, updated.captured.status)
    }

    @Test
    fun `updating an entry that is not loaded reports entry not found`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry(1)))
        val viewModel = viewModel()

        viewModel.updateEntryStatus(entryId = 999L, newStatus = Status.COMPLETED)

        assertEquals(
            AppConstants.Errors.ENTRY_NOT_FOUND,
            viewModel.uiState.value.errorMessage
        )
        coVerify(exactly = 0) { updateWorkEntryUseCase(any()) }
    }

    @Test
    fun `a failing status update surfaces its message as the error`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry(1)))
        coEvery { updateWorkEntryUseCase(any()) } throws RuntimeException("write failed")
        val viewModel = viewModel()

        viewModel.updateEntryStatus(entryId = 1L, newStatus = Status.DELIVERED)

        assertEquals("write failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `updateWorkEntry delegates the entry unchanged to the use case`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        val toUpdate = entry(5, title = "Press seams", status = Status.DELIVERED)
        val captured = slot<WorkEntry>()
        coEvery { updateWorkEntryUseCase(capture(captured)) } returns Unit

        viewModel().updateWorkEntry(toUpdate)

        assertEquals(toUpdate, captured.captured)
    }

    // --- Derived state ------------------------------------------------------

    @Test
    fun `derived counts reflect the loaded entries`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(
            listOf(
                entry(1, status = Status.IN_PROGRESS),
                entry(2, status = Status.COMPLETED),
                entry(3, status = Status.COMPLETED),
                entry(4, status = Status.DELIVERED)
            )
        )

        val state = viewModel().uiState.value

        assertEquals(4, state.todayEntries.size)
        // completedToday counts finished work, which includes what has already
        // been handed over — two completed plus one delivered.
        assertEquals(3, state.completedToday)
        assertFalse(state.isEmpty)
    }

    // --- Undelivered band ---------------------------------------------------

    @Test
    fun `completed work from any date reaches the undelivered band`() = runTest {
        val oldJob = WorkEntry(
            id = 9, title = "Finished last week", status = Status.COMPLETED,
            date = LocalDate.of(2026, 8, 21)
        )
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(listOf(oldJob))

        val viewModel = viewModel()
        advanceUntilIdle()

        // The whole point: it is not from today, and it must still show.
        assertEquals(listOf(oldJob), viewModel.uiState.value.undelivered)
        assertEquals(1, viewModel.uiState.value.undeliveredCount)
    }

    @Test
    fun `undelivered work alone means the screen is not empty`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(
            listOf(WorkEntry(id = 9, title = "Waiting", status = Status.COMPLETED,
                date = LocalDate.of(2026, 8, 21)))
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEmpty)
    }

    // --- Status lifecycle ---------------------------------------------------

    @Test
    fun `advancing in-progress work completes it`() = runTest {
        val entry = WorkEntry(id = 1, title = "Job", status = Status.IN_PROGRESS,
            date = LocalDate.of(2026, 8, 28))
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry))
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceStatus(entry)
        advanceUntilIdle()

        coVerify { updateWorkEntryUseCase(entry.copy(status = Status.COMPLETED)) }
    }

    @Test
    fun `advancing completed work delivers it`() = runTest {
        val entry = WorkEntry(id = 1, title = "Job", status = Status.COMPLETED,
            date = LocalDate.of(2026, 8, 28))
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry))
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceStatus(entry)
        advanceUntilIdle()

        coVerify { updateWorkEntryUseCase(entry.copy(status = Status.DELIVERED)) }
    }

    @Test
    fun `delivered work has no further transition`() = runTest {
        val entry = WorkEntry(id = 1, title = "Job", status = Status.DELIVERED,
            date = LocalDate.of(2026, 8, 28))
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry))
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceStatus(entry)
        advanceUntilIdle()

        coVerify(exactly = 0) { updateWorkEntryUseCase(any()) }
        assertNull(viewModel.uiState.value.recentlyAdvanced)
    }

    @Test
    fun `undo restores the exact previous status`() = runTest {
        val entry = WorkEntry(id = 1, title = "Job", status = Status.IN_PROGRESS,
            date = LocalDate.of(2026, 8, 28))
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(listOf(entry))
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceStatus(entry)
        advanceUntilIdle()
        viewModel.undoAdvance()
        advanceUntilIdle()

        // The pre-change entry is written back, rather than a guessed reverse step.
        coVerify { updateWorkEntryUseCase(entry) }
        assertNull(viewModel.uiState.value.recentlyAdvanced)
    }

    // --- Quick add ----------------------------------------------------------

    @Test
    fun `quick add creates an in-progress entry dated today with no quantity`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())
        val captured = slot<WorkEntry>()
        coEvery { insertWorkEntryUseCase(capture(captured)) } returns 1L

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.quickAdd("  Hem trousers  ")
        advanceUntilIdle()

        assertEquals("Hem trousers", captured.captured.title)
        assertEquals(Status.IN_PROGRESS, captured.captured.status)
        assertNull(captured.captured.quantity)
    }

    @Test
    fun `quick add ignores blank input rather than reporting an error`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.quickAdd("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { insertWorkEntryUseCase(any()) }
        // Pressing done on an empty field is a change of mind, not a mistake.
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `quick add stores the quantity when one is given`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())
        val captured = slot<WorkEntry>()
        coEvery { insertWorkEntryUseCase(capture(captured)) } returns 1L

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.quickAdd("Hem trousers", 12)
        advanceUntilIdle()

        // Quantity is what a pieceworker bills on and what Reports totals, so
        // the fast capture path has to be able to carry it.
        assertEquals(12, captured.captured.quantity)
    }

    @Test
    fun `quick add without a quantity leaves it null rather than zero`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())
        val captured = slot<WorkEntry>()
        coEvery { insertWorkEntryUseCase(capture(captured)) } returns 1L

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.quickAdd("Hem trousers", null)
        advanceUntilIdle()

        // Zero would be a claim that nothing was produced; null is "not counted".
        assertNull(captured.captured.quantity)
    }

    // --- Undelivered band cap -----------------------------------------------

    @Test
    fun `the band shows only the oldest few but counts them all`() = runTest {
        val many = (1..10).map {
            WorkEntry(
                id = it.toLong(), title = "Job $it", status = Status.COMPLETED,
                date = LocalDate.of(2026, 8, 1).plusDays(it.toLong())
            )
        }
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(many)

        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value

        // An uncapped band buries today's work under months of history.
        assertEquals(3, state.undeliveredPreview.size)
        assertTrue(state.hasMoreUndelivered)
        // The header still tells the truth about how much is owed.
        assertEquals(10, state.undeliveredCount)
        // Oldest first: those are the ones worth chasing.
        assertEquals(listOf(1L, 2L, 3L), state.undeliveredPreview.map { it.id })
    }

    @Test
    fun `a short band offers no see-all`() = runTest {
        every { getWorkEntriesByDateUseCase(any()) } returns flowOf(emptyList())
        every { getUndeliveredWorkUseCase() } returns flowOf(
            listOf(WorkEntry(id = 1, title = "One", status = Status.COMPLETED,
                date = LocalDate.of(2026, 8, 28)))
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMoreUndelivered)
        assertEquals(1, viewModel.uiState.value.undeliveredPreview.size)
    }
}
