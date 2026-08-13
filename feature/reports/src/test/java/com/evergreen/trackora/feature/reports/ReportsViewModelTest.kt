package com.evergreen.trackora.feature.reports

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateRangeUseCase
import com.evergreen.trackora.domain.usecase.GetWorkEntriesByDateUseCase
import com.evergreen.trackora.util.AppConstants
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getWorkEntriesByDateUseCase: GetWorkEntriesByDateUseCase = mockk()
    private val getWorkEntriesByDateRangeUseCase: GetWorkEntriesByDateRangeUseCase = mockk()

    private val today: LocalDate = LocalDate.now()

    private fun entry(
        id: Long,
        status: Status = Status.COMPLETED,
        quantity: Int? = null
    ) = WorkEntry(
        id = id,
        title = "Entry $id",
        status = status,
        date = today,
        quantity = quantity
    )

    private fun viewModel() = ReportsViewModel(
        getWorkEntriesByDateUseCase,
        getWorkEntriesByDateRangeUseCase
    )

    /** Stubs today, the 7-day window and the 30-day window respectively. */
    private fun stub(
        daily: List<WorkEntry> = emptyList(),
        weekly: List<WorkEntry> = emptyList(),
        monthly: List<WorkEntry> = emptyList()
    ) {
        every { getWorkEntriesByDateUseCase(today) } returns flowOf(daily)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(6), today)
        } returns flowOf(weekly)
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(29), today)
        } returns flowOf(monthly)
    }

    // --- Date ranges --------------------------------------------------------

    @Test
    fun `the three windows are queried as today, the last 7 days and the last 30 days`() =
        runTest {
            // Both ranges are inclusive of today, so a 7-day window ends 6 days
            // back and a 30-day window 29 days back. An off-by-one here is
            // invisible in the UI but quietly wrong in every report.
            stub()

            viewModel()

            io.mockk.verify { getWorkEntriesByDateUseCase(today) }
            io.mockk.verify { getWorkEntriesByDateRangeUseCase(today.minusDays(6), today) }
            io.mockk.verify { getWorkEntriesByDateRangeUseCase(today.minusDays(29), today) }
        }

    // --- Aggregation --------------------------------------------------------

    @Test
    fun `completed and delivered are counted separately`() = runTest {
        stub(
            daily = listOf(
                entry(1, Status.COMPLETED),
                entry(2, Status.COMPLETED),
                entry(3, Status.DELIVERED),
                entry(4, Status.IN_PROGRESS)
            )
        )

        val daily = viewModel().uiState.value.daily

        assertEquals(2, daily.completed)
        assertEquals(1, daily.delivered)
    }

    @Test
    fun `in-progress entries count towards neither total`() = runTest {
        stub(daily = List(5) { entry(it.toLong(), Status.IN_PROGRESS) })

        val daily = viewModel().uiState.value.daily

        assertEquals(0, daily.completed)
        assertEquals(0, daily.delivered)
    }

    @Test
    fun `quantities are summed across all entries regardless of status`() = runTest {
        stub(
            daily = listOf(
                entry(1, Status.COMPLETED, quantity = 3),
                entry(2, Status.DELIVERED, quantity = 4),
                entry(3, Status.IN_PROGRESS, quantity = 5)
            )
        )

        assertEquals(12, viewModel().uiState.value.daily.totalQuantity)
    }

    @Test
    fun `a null quantity contributes zero rather than failing`() = runTest {
        stub(
            daily = listOf(
                entry(1, quantity = 7),
                entry(2, quantity = null),
                entry(3, quantity = null)
            )
        )

        assertEquals(7, viewModel().uiState.value.daily.totalQuantity)
    }

    @Test
    fun `an empty window reports zeroes`() = runTest {
        stub()

        val state = viewModel().uiState.value

        assertEquals(0, state.daily.completed)
        assertEquals(0, state.daily.delivered)
        assertEquals(0, state.daily.totalQuantity)
    }

    // --- The three windows are kept distinct --------------------------------

    @Test
    fun `each window is summarised from its own entries`() = runTest {
        stub(
            daily = listOf(entry(1, Status.COMPLETED, quantity = 1)),
            weekly = List(5) { entry(it.toLong(), Status.COMPLETED, quantity = 2) },
            monthly = List(20) { entry(it.toLong(), Status.DELIVERED, quantity = 3) }
        )

        val state = viewModel().uiState.value

        assertEquals(1, state.daily.completed)
        assertEquals(1, state.daily.totalQuantity)

        assertEquals(5, state.weekly.completed)
        assertEquals(10, state.weekly.totalQuantity)

        assertEquals(20, state.monthly.delivered)
        assertEquals(0, state.monthly.completed)
        assertEquals(60, state.monthly.totalQuantity)
    }

    @Test
    fun `loading is cleared once all three windows have reported`() = runTest {
        stub(daily = listOf(entry(1)))

        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    // --- Error path ---------------------------------------------------------

    @Test
    fun `a failure in any window sets the error and clears loading`() = runTest {
        every { getWorkEntriesByDateUseCase(today) } returns flowOf(emptyList())
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(6), today)
        } returns flow { throw RuntimeException("range query failed") }
        every {
            getWorkEntriesByDateRangeUseCase(today.minusDays(29), today)
        } returns flowOf(emptyList())

        val state = viewModel().uiState.value

        assertEquals("range query failed", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failure without a message falls back to the generic error`() = runTest {
        every { getWorkEntriesByDateUseCase(today) } returns flow { throw RuntimeException() }
        every {
            getWorkEntriesByDateRangeUseCase(any(), any())
        } returns flowOf(emptyList())

        assertEquals(
            AppConstants.Errors.FAILED_TO_LOAD_ENTRIES,
            viewModel().uiState.value.errorMessage
        )
    }
}
