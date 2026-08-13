package com.evergreen.trackora.feature.allwork

import app.cash.turbine.test
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetAllWorkEntriesUseCase
import com.evergreen.trackora.util.AppConstants
import io.mockk.every
import io.mockk.mockk
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
class AllWorkViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getAllWorkEntriesUseCase: GetAllWorkEntriesUseCase = mockk()

    private fun entry(id: Long, status: Status) = WorkEntry(
        id = id,
        title = "Entry $id",
        status = status,
        date = LocalDate.of(2026, 3, 21)
    )

    private val mixedEntries = listOf(
        entry(1, Status.IN_PROGRESS),
        entry(2, Status.COMPLETED),
        entry(3, Status.COMPLETED),
        entry(4, Status.DELIVERED)
    )

    private fun viewModel() = AllWorkViewModel(getAllWorkEntriesUseCase)

    // --- Loading ------------------------------------------------------------

    @Test
    fun `entries land in state with loading cleared`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(mixedEntries)

        viewModel().uiState.test {
            val state = awaitItem()

            assertEquals(mixedEntries, state.entries)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `state starts loading and stays loading until entries arrive`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entries = MutableSharedFlow<List<WorkEntry>>()
        every { getAllWorkEntriesUseCase() } returns entries

        val viewModel = viewModel()

        // Unlike TodayViewModel this one seeds isLoading = true in its initial
        // state rather than setting it inside the coroutine, so it is already
        // loading before the dispatcher runs anything.
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        entries.emit(mixedEntries)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(4, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `an empty result clears loading and leaves no entries`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(emptyList())

        val state = viewModel().uiState.value

        assertTrue(state.entries.isEmpty())
        assertFalse(state.isLoading)
    }

    // --- Error path ---------------------------------------------------------

    @Test
    fun `a failing flow sets the error message and clears loading`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flow { throw RuntimeException("query failed") }

        val state = viewModel().uiState.value

        assertEquals("query failed", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failure without a message falls back to the generic error`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flow { throw RuntimeException() }

        assertEquals(
            AppConstants.Errors.FAILED_TO_LOAD_ENTRIES,
            viewModel().uiState.value.errorMessage
        )
    }

    // --- Filtering ----------------------------------------------------------

    @Test
    fun `no filter shows every entry`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(mixedEntries)

        val state = viewModel().uiState.value

        assertNull(state.filter)
        assertEquals(mixedEntries, state.filteredEntries)
    }

    @Test
    fun `setting a filter narrows the visible entries without discarding the rest`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(mixedEntries)
        val viewModel = viewModel()

        viewModel.setFilter(Status.COMPLETED)

        val state = viewModel.uiState.value
        assertEquals(Status.COMPLETED, state.filter)
        assertEquals(listOf(2L, 3L), state.filteredEntries.map { it.id })
        // The unfiltered list is retained so clearing the filter needs no reload.
        assertEquals(4, state.entries.size)
    }

    @Test
    fun `clearing the filter restores the full list`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(mixedEntries)
        val viewModel = viewModel()

        viewModel.setFilter(Status.DELIVERED)
        assertEquals(1, viewModel.uiState.value.filteredEntries.size)

        viewModel.setFilter(null)

        assertNull(viewModel.uiState.value.filter)
        assertEquals(mixedEntries, viewModel.uiState.value.filteredEntries)
    }

    @Test
    fun `every status filters to the entries carrying it`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(mixedEntries)
        val viewModel = viewModel()

        val expectedCounts = mapOf(
            Status.IN_PROGRESS to 1,
            Status.COMPLETED to 2,
            Status.DELIVERED to 1
        )

        expectedCounts.forEach { (status, expected) ->
            viewModel.setFilter(status)
            val filtered = viewModel.uiState.value.filteredEntries

            assertEquals("count for $status", expected, filtered.size)
            assertTrue("all entries match $status", filtered.all { it.status == status })
        }
    }

    @Test
    fun `a filter matching nothing yields an empty list rather than everything`() = runTest {
        every { getAllWorkEntriesUseCase() } returns flowOf(
            listOf(entry(1, Status.IN_PROGRESS))
        )
        val viewModel = viewModel()

        viewModel.setFilter(Status.DELIVERED)

        assertTrue(viewModel.uiState.value.filteredEntries.isEmpty())
    }

    @Test
    fun `the filter survives a later emission from the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val entries = MutableSharedFlow<List<WorkEntry>>()
        every { getAllWorkEntriesUseCase() } returns entries

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setFilter(Status.COMPLETED)
        entries.emit(mixedEntries + entry(5, Status.COMPLETED))
        advanceUntilIdle()

        // A refresh from the database must not silently reset the user's filter.
        assertEquals(Status.COMPLETED, viewModel.uiState.value.filter)
        assertEquals(3, viewModel.uiState.value.filteredEntries.size)
    }
}
