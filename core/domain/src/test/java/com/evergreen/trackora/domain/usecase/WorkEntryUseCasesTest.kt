package com.evergreen.trackora.domain.usecase

import app.cash.turbine.test
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.domain.repository.WorkEntryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the eight work entry use cases.
 *
 * Use cases are thin delegating wrappers, so the contract worth pinning down is
 * that each one forwards to the correct repository method with unmodified
 * arguments and returns the repository's result untouched. A use case that
 * silently swallowed a result or reordered a date range would break the screens
 * above it, and nothing else in the codebase would catch that.
 */
class WorkEntryUseCasesTest {

    private lateinit var repository: WorkEntryRepository

    private val entry = WorkEntry(
        id = 1L,
        title = "Sew lining",
        description = "Navy wool",
        quantity = 12,
        status = Status.IN_PROGRESS,
        date = LocalDate.of(2026, 3, 21)
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
    }

    // --- Queries -----------------------------------------------------------

    @Test
    fun `GetAllWorkEntriesUseCase emits entries from the repository`() = runTest {
        every { repository.getAllEntries() } returns flowOf(listOf(entry))

        GetAllWorkEntriesUseCase(repository)().test {
            assertEquals(listOf(entry), awaitItem())
            awaitComplete()
        }
        verify(exactly = 1) { repository.getAllEntries() }
    }

    @Test
    fun `GetAllWorkEntriesUseCase emits an empty list when there are no entries`() = runTest {
        every { repository.getAllEntries() } returns flowOf(emptyList())

        GetAllWorkEntriesUseCase(repository)().test {
            assertEquals(emptyList<WorkEntry>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `GetWorkEntriesByDateUseCase passes the requested date through unchanged`() = runTest {
        val date = LocalDate.of(2026, 3, 21)
        every { repository.getEntriesByDate(date) } returns flowOf(listOf(entry))

        GetWorkEntriesByDateUseCase(repository)(date).test {
            assertEquals(listOf(entry), awaitItem())
            awaitComplete()
        }
        verify(exactly = 1) { repository.getEntriesByDate(date) }
    }

    @Test
    fun `GetWorkEntriesByDateUseCase re-emits when the repository emits again`() = runTest {
        val date = LocalDate.of(2026, 3, 21)
        every { repository.getEntriesByDate(date) } returns flowOf(emptyList(), listOf(entry))

        GetWorkEntriesByDateUseCase(repository)(date).test {
            assertEquals(emptyList<WorkEntry>(), awaitItem())
            assertEquals(listOf(entry), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `GetWorkEntriesByDateRangeUseCase preserves start and end order`() = runTest {
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 3, 31)
        every { repository.getEntriesByDateRange(start, end) } returns flowOf(listOf(entry))

        GetWorkEntriesByDateRangeUseCase(repository)(start, end).test {
            assertEquals(listOf(entry), awaitItem())
            awaitComplete()
        }
        // Guards against the start/end arguments being swapped on the way down.
        verify(exactly = 1) { repository.getEntriesByDateRange(start, end) }
    }

    @Test
    fun `GetWorkEntryByIdUseCase returns the entry for a known id`() = runTest {
        coEvery { repository.getEntryById(1L) } returns entry

        val result = GetWorkEntryByIdUseCase(repository)(1L)

        assertSame(entry, result)
        coVerify(exactly = 1) { repository.getEntryById(1L) }
    }

    @Test
    fun `GetWorkEntryByIdUseCase returns null for an unknown id`() = runTest {
        coEvery { repository.getEntryById(99L) } returns null

        assertNull(GetWorkEntryByIdUseCase(repository)(99L))
    }

    // --- Mutations ---------------------------------------------------------

    @Test
    fun `InsertWorkEntryUseCase returns the new row id from the repository`() = runTest {
        coEvery { repository.insertEntry(entry) } returns 42L

        val id = InsertWorkEntryUseCase(repository)(entry)

        assertEquals(42L, id)
        coVerify(exactly = 1) { repository.insertEntry(entry) }
    }

    @Test
    fun `InsertWorkEntryUseCase forwards the entry without modifying it`() = runTest {
        coEvery { repository.insertEntry(any()) } returns 1L

        InsertWorkEntryUseCase(repository)(entry)

        coVerify { repository.insertEntry(match { it == entry }) }
    }

    @Test
    fun `UpdateWorkEntryUseCase delegates to the repository`() = runTest {
        UpdateWorkEntryUseCase(repository)(entry)

        coVerify(exactly = 1) { repository.updateEntry(entry) }
    }

    @Test
    fun `DeleteWorkEntryUseCase delegates to the repository`() = runTest {
        DeleteWorkEntryUseCase(repository)(entry)

        coVerify(exactly = 1) { repository.deleteEntry(entry) }
    }

    @Test
    fun `DeleteWorkEntryByIdUseCase delegates to the repository`() = runTest {
        DeleteWorkEntryByIdUseCase(repository)(7L)

        coVerify(exactly = 1) { repository.deleteEntryById(7L) }
    }

    @Test
    fun `use cases propagate repository failures rather than swallowing them`() = runTest {
        val boom = IllegalStateException("database unavailable")
        coEvery { repository.insertEntry(any()) } throws boom

        val thrown = runCatching { InsertWorkEntryUseCase(repository)(entry) }.exceptionOrNull()

        assertSame(boom, thrown)
    }
}
