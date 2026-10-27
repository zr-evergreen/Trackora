package com.evergreen.trackora.feature.reports

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetUndeliveredWorkUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateRangeUseCase
import com.evergreen.trackora.util.AppConstants
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the rebuilt reports.
 *
 * The screen used to print the same figure twice and had no notion of a
 * previous period. It now answers four questions — how much was finished, how
 * many units, how that compares with the window before, and how much is still
 * undelivered — so the arithmetic behind each is worth pinning down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getWorkEntriesByDateRangeUseCase: GetWorkEntriesByDateRangeUseCase = mockk()
    private val getUndeliveredWorkUseCase: GetUndeliveredWorkUseCase = mockk()

    private val today: LocalDate = LocalDate.now()

    private fun entry(
        id: Long,
        status: Status = Status.COMPLETED,
        quantity: Int? = null
    ) = WorkEntry(id = id, title = "Entry $id", status = status, date = today, quantity = quantity)

    private fun viewModel() = ReportsViewModel(
        getWorkEntriesByDateRangeUseCase,
        getUndeliveredWorkUseCase
    )

    /**
     * Stubs each window and the equal-length window preceding it.
     *
     * Every range is queried as a range now, today included — today..today —
     * so that current and previous windows are built the same way.
     */
    private fun stub(
        todayEntries: List<WorkEntry> = emptyList(),
        yesterdayEntries: List<WorkEntry> = emptyList(),
        weekEntries: List<WorkEntry> = emptyList(),
        previousWeekEntries: List<WorkEntry> = emptyList(),
        monthEntries: List<WorkEntry> = emptyList(),
        previousMonthEntries: List<WorkEntry> = emptyList(),
        undelivered: List<WorkEntry> = emptyList()
    ) {
        every { getWorkEntriesByDateRangeUseCase(today, today) } returns flowOf(todayEntries)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(1), today.minusDays(1))
        } returns flowOf(yesterdayEntries)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(6), today)
        } returns flowOf(weekEntries)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(13), today.minusDays(7))
        } returns flowOf(previousWeekEntries)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(29), today)
        } returns flowOf(monthEntries)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(59), today.minusDays(30))
        } returns flowOf(previousMonthEntries)
        every { getUndeliveredWorkUseCase() } returns flowOf(undelivered)
    }

    // --- Counting -----------------------------------------------------------

    @Test
    fun `completed counts finished work whether or not it has been delivered`() = runTest {
        // Delivering something does not un-complete it. Counting only COMPLETED
        // would show a user's output falling as they hand work over.
        stub(
            todayEntries = listOf(
                entry(1, Status.IN_PROGRESS),
                entry(2, Status.COMPLETED),
                entry(3, Status.DELIVERED)
            )
        )

        val state = viewModel().uiState.value

        assertEquals(2, state.daily.completed)
    }

    @Test
    fun `quantity sums the window and treats a missing quantity as zero`() = runTest {
        stub(
            todayEntries = listOf(
                entry(1, quantity = 12),
                entry(2, quantity = null),
                entry(3, quantity = 30)
            )
        )

        assertEquals(42, viewModel().uiState.value.daily.totalQuantity)
    }

    // --- Comparison with the previous window --------------------------------

    @Test
    fun `a better week reports the difference as a rise`() = runTest {
        stub(
            weekEntries = listOf(entry(1), entry(2), entry(3)),
            previousWeekEntries = listOf(entry(4))
        )

        assertEquals(2, viewModel().uiState.value.weekly.changeVsPrevious)
    }

    @Test
    fun `a worse month reports a negative difference`() = runTest {
        stub(
            monthEntries = listOf(entry(1)),
            previousMonthEntries = listOf(entry(2), entry(3), entry(4))
        )

        assertEquals(-2, viewModel().uiState.value.monthly.changeVsPrevious)
    }

    @Test
    fun `two empty windows report no comparison rather than a change of zero`() = runTest {
        // "0 fewer than the period before" is noise on a screen that should
        // simply say nothing was recorded.
        stub()

        assertNull(viewModel().uiState.value.daily.changeVsPrevious)
    }

    @Test
    fun `an unchanged period reports zero, which the screen then hides`() = runTest {
        stub(todayEntries = listOf(entry(1)), yesterdayEntries = listOf(entry(2)))

        assertEquals(0, viewModel().uiState.value.daily.changeVsPrevious)
    }

    // --- Undelivered --------------------------------------------------------

    @Test
    fun `undelivered is a present-tense figure, not scoped to the window`() = runTest {
        // The job was finished long before any of these windows and is still
        // uncollected; it must not disappear because the user picked "today".
        stub(undelivered = listOf(entry(9), entry(10)))

        val state = viewModel().uiState.value

        assertEquals(2, state.undeliveredCount)
        assertEquals(0, state.daily.completed)
    }

    // --- States -------------------------------------------------------------

    @Test
    fun `an empty database is reported as no activity`() = runTest {
        stub()

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.daily.hasActivity)
        assertEquals(0, state.undeliveredCount)
    }

    @Test
    fun `recorded work counts as activity`() = runTest {
        stub(todayEntries = listOf(entry(1, quantity = 5)))

        assertTrue(viewModel().uiState.value.daily.hasActivity)
    }

    @Test
    fun `a failing query surfaces an error and stops loading`() = runTest {
        every { getWorkEntriesByDateRangeUseCase(any(), any()) } returns
            flow { throw RuntimeException("db gone") }
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertEquals("db gone", state.errorMessage)
    }

    @Test
    fun `retry clears the error before reloading`() = runTest {
        every { getWorkEntriesByDateRangeUseCase(any(), any()) } returns
            flow { throw RuntimeException(AppConstants.Errors.FAILED_TO_LOAD_ENTRIES) }
        every { getUndeliveredWorkUseCase() } returns flowOf(emptyList())

        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(AppConstants.Errors.FAILED_TO_LOAD_ENTRIES, viewModel.uiState.value.errorMessage)

        stub()
        viewModel.retry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
