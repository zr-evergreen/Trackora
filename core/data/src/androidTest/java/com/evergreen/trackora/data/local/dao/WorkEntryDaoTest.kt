package com.evergreen.trackora.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.evergreen.trackora.data.local.database.TrackoraDatabase
import com.evergreen.trackora.data.local.entity.WorkEntry
import com.evergreen.trackora.domain.model.Status
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Instrumentation tests for [WorkEntryDao] against a real in-memory SQLite
 * database.
 *
 * These have to run on a device rather than as JVM unit tests because they are
 * exercising the parts nobody can fake: the generated Room query
 * implementations, and the [com.evergreen.trackora.data.local.converter.Converters]
 * round trip that stores `LocalDate` as an ISO-8601 string.
 *
 * That storage choice is what the date-range tests are really about. The
 * queries compare dates with `>=` and `<=`, which SQLite evaluates
 * lexicographically on TEXT. It happens to give the right answer only because
 * ISO-8601 with zero-padded months and days sorts identically to chronological
 * order. Switching the formatter to anything else — `d/M/yyyy`, say — would
 * leave every unit test in the project passing while silently corrupting every
 * report the user runs, so the boundary and ordering assertions below are the
 * only place that assumption is actually checked.
 */
@RunWith(AndroidJUnit4::class)
class WorkEntryDaoTest {

    private lateinit var database: TrackoraDatabase
    private lateinit var dao: WorkEntryDao

    private fun entry(
        title: String,
        date: LocalDate,
        status: Status = Status.IN_PROGRESS,
        quantity: Int? = null,
    ) = WorkEntry(
        title = title,
        description = null,
        quantity = quantity,
        status = status,
        date = date,
        photoUri = null
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackoraDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.workEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // --- Insert and read back ----------------------------------------------

    @Test
    fun insertReturnsGeneratedIdAndEntryIsReadableById() = runTest {
        val id = dao.insertEntry(entry("Hem trousers", LocalDate.of(2026, 3, 21)))

        assertTrue("expected a generated id", id > 0)
        val loaded = dao.getEntryById(id)
        assertEquals("Hem trousers", loaded?.title)
        assertEquals(LocalDate.of(2026, 3, 21), loaded?.date)
        assertEquals(Status.IN_PROGRESS, loaded?.status)
    }

    @Test
    fun everyFieldSurvivesTheRoundTripThroughSqlite() = runTest {
        val id = dao.insertEntry(
            WorkEntry(
                title = "Shorten sleeves",
                description = "Navy blazer",
                quantity = 2,
                status = Status.DELIVERED,
                date = LocalDate.of(2026, 4, 9),
                customField1 = "Order 91",
                customField2 = "Mr. Karimi",
                customField3 = "Paid",
                photoUri = "content://photo/9"
            )
        )

        val loaded = dao.getEntryById(id)!!

        assertEquals("Shorten sleeves", loaded.title)
        assertEquals("Navy blazer", loaded.description)
        assertEquals(2, loaded.quantity)
        assertEquals(Status.DELIVERED, loaded.status)
        assertEquals(LocalDate.of(2026, 4, 9), loaded.date)
        assertEquals("Order 91", loaded.customField1)
        assertEquals("Mr. Karimi", loaded.customField2)
        assertEquals("Paid", loaded.customField3)
        assertEquals("content://photo/9", loaded.photoUri)
    }

    @Test
    fun nullableColumnsComeBackAsNull() = runTest {
        val id = dao.insertEntry(entry("Cut fabric", LocalDate.of(2026, 1, 5)))

        val loaded = dao.getEntryById(id)!!

        assertNull(loaded.description)
        assertNull(loaded.quantity)
        assertNull(loaded.customField1)
        assertNull(loaded.customField2)
        assertNull(loaded.customField3)
        assertNull(loaded.photoUri)
    }

    @Test
    fun getEntryByIdReturnsNullForAnIdThatDoesNotExist() = runTest {
        assertNull(dao.getEntryById(4242L))
    }

    // --- Update and delete -------------------------------------------------

    @Test
    fun updateEntryOverwritesTheStoredRow() = runTest {
        val id = dao.insertEntry(entry("Draft", LocalDate.of(2026, 5, 1)))
        val stored = dao.getEntryById(id)!!

        dao.updateEntry(
            stored.copy(
                title = "Final",
                status = Status.COMPLETED,
                quantity = 7,
                date = LocalDate.of(2026, 5, 2)
            )
        )

        val updated = dao.getEntryById(id)!!
        assertEquals("Final", updated.title)
        assertEquals(Status.COMPLETED, updated.status)
        assertEquals(7, updated.quantity)
        assertEquals(LocalDate.of(2026, 5, 2), updated.date)
        assertEquals(1, dao.getAllEntries().first().size)
    }

    @Test
    fun deleteEntryRemovesOnlyThatRow() = runTest {
        val keptId = dao.insertEntry(entry("Keep", LocalDate.of(2026, 5, 1)))
        val doomedId = dao.insertEntry(entry("Delete", LocalDate.of(2026, 5, 1)))

        dao.deleteEntry(dao.getEntryById(doomedId)!!)

        assertNull(dao.getEntryById(doomedId))
        assertEquals("Keep", dao.getEntryById(keptId)?.title)
    }

    @Test
    fun deleteEntryByIdRemovesTheRow() = runTest {
        val id = dao.insertEntry(entry("Temporary", LocalDate.of(2026, 5, 1)))

        dao.deleteEntryById(id)

        assertNull(dao.getEntryById(id))
        assertTrue(dao.getAllEntries().first().isEmpty())
    }

    @Test
    fun deleteEntryByIdIsANoOpForAnUnknownId() = runTest {
        dao.insertEntry(entry("Survivor", LocalDate.of(2026, 5, 1)))

        dao.deleteEntryById(9999L)

        assertEquals(1, dao.getAllEntries().first().size)
    }

    // --- Query by date -----------------------------------------------------

    @Test
    fun getEntriesByDateReturnsOnlyThatDay() = runTest {
        dao.insertEntry(entry("Yesterday", LocalDate.of(2026, 3, 20)))
        dao.insertEntry(entry("Today A", LocalDate.of(2026, 3, 21)))
        dao.insertEntry(entry("Today B", LocalDate.of(2026, 3, 21)))
        dao.insertEntry(entry("Tomorrow", LocalDate.of(2026, 3, 22)))

        val result = dao.getEntriesByDate(LocalDate.of(2026, 3, 21)).first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.date == LocalDate.of(2026, 3, 21) })
    }

    @Test
    fun getEntriesByDateOrdersNewestFirst() = runTest {
        dao.insertEntry(entry("First added", LocalDate.of(2026, 3, 21)))
        dao.insertEntry(entry("Second added", LocalDate.of(2026, 3, 21)))
        dao.insertEntry(entry("Third added", LocalDate.of(2026, 3, 21)))

        val titles = dao.getEntriesByDate(LocalDate.of(2026, 3, 21)).first().map { it.title }

        // Ordered by id DESC, so the most recently added entry appears on top.
        assertEquals(listOf("Third added", "Second added", "First added"), titles)
    }

    @Test
    fun getEntriesByDateIsEmptyWhenNothingMatches() = runTest {
        dao.insertEntry(entry("Elsewhere", LocalDate.of(2026, 3, 20)))

        assertTrue(dao.getEntriesByDate(LocalDate.of(2026, 3, 21)).first().isEmpty())
    }

    // --- Query by range ----------------------------------------------------

    @Test
    fun getEntriesByDateRangeIncludesBothBoundaries() = runTest {
        dao.insertEntry(entry("Before", LocalDate.of(2026, 2, 28)))
        dao.insertEntry(entry("Start", LocalDate.of(2026, 3, 1)))
        dao.insertEntry(entry("Middle", LocalDate.of(2026, 3, 15)))
        dao.insertEntry(entry("End", LocalDate.of(2026, 3, 31)))
        dao.insertEntry(entry("After", LocalDate.of(2026, 4, 1)))

        val titles = dao.getEntriesByDateRange(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31)
        ).first().map { it.title }

        // Inclusive on both ends — an off-by-one here drops a day from every
        // monthly report.
        assertEquals(3, titles.size)
        assertTrue(titles.containsAll(listOf("Start", "Middle", "End")))
    }

    @Test
    fun getEntriesByDateRangeOrdersByDateDescending() = runTest {
        dao.insertEntry(entry("Oldest", LocalDate.of(2026, 3, 2)))
        dao.insertEntry(entry("Newest", LocalDate.of(2026, 3, 30)))
        dao.insertEntry(entry("Middle", LocalDate.of(2026, 3, 16)))

        val titles = dao.getEntriesByDateRange(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31)
        ).first().map { it.title }

        assertEquals(listOf("Newest", "Middle", "Oldest"), titles)
    }

    @Test
    fun dateRangeComparesChronologicallyNotAsRawText() = runTest {
        // Single-digit months and days are where a non-padded date format would
        // break: "2026-9-05" sorts after "2026-10-05" as text.
        dao.insertEntry(entry("September", LocalDate.of(2026, 9, 5)))
        dao.insertEntry(entry("October", LocalDate.of(2026, 10, 5)))
        dao.insertEntry(entry("November", LocalDate.of(2026, 11, 5)))

        val titles = dao.getEntriesByDateRange(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 31)
        ).first().map { it.title }

        assertEquals(listOf("October", "September"), titles)
    }

    @Test
    fun dateRangeSpansAYearBoundary() = runTest {
        dao.insertEntry(entry("Late December", LocalDate.of(2026, 12, 28)))
        dao.insertEntry(entry("Early January", LocalDate.of(2027, 1, 3)))
        dao.insertEntry(entry("Well after", LocalDate.of(2027, 3, 1)))

        val titles = dao.getEntriesByDateRange(
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 1, 31)
        ).first().map { it.title }

        assertEquals(listOf("Early January", "Late December"), titles)
    }

    // --- Counts ------------------------------------------------------------

    @Test
    fun getEntryCountByDateCountsOnlyThatDay() = runTest {
        dao.insertEntry(entry("A", LocalDate.of(2026, 3, 21)))
        dao.insertEntry(entry("B", LocalDate.of(2026, 3, 21)))
        dao.insertEntry(entry("C", LocalDate.of(2026, 3, 22)))

        assertEquals(2, dao.getEntryCountByDate(LocalDate.of(2026, 3, 21)).first())
        assertEquals(0, dao.getEntryCountByDate(LocalDate.of(2026, 3, 23)).first())
    }

    @Test
    fun getEntryCountByDateRangeMatchesTheRangeQuery() = runTest {
        dao.insertEntry(entry("Before", LocalDate.of(2026, 2, 28)))
        dao.insertEntry(entry("Start", LocalDate.of(2026, 3, 1)))
        dao.insertEntry(entry("End", LocalDate.of(2026, 3, 31)))

        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 3, 31)

        // The count and the list are separate SQL statements; they must not
        // disagree, or the reports header contradicts the list under it.
        assertEquals(
            dao.getEntriesByDateRange(start, end).first().size,
            dao.getEntryCountByDateRange(start, end).first()
        )
        assertEquals(2, dao.getEntryCountByDateRange(start, end).first())
    }

    // --- Query by status ---------------------------------------------------

    @Test
    fun getEntriesByStatusFiltersOnTheStoredEnumName() = runTest {
        dao.insertEntry(entry("Doing", LocalDate.of(2026, 3, 21), Status.IN_PROGRESS))
        dao.insertEntry(entry("Done", LocalDate.of(2026, 3, 21), Status.COMPLETED))
        dao.insertEntry(entry("Gone", LocalDate.of(2026, 3, 21), Status.DELIVERED))

        // The DAO takes a String, so the caller passes Status.name — this
        // asserts those two conventions still line up.
        assertEquals(listOf("Doing"), dao.getEntriesByStatus(Status.IN_PROGRESS.name).map { it.title })
        assertEquals(listOf("Done"), dao.getEntriesByStatus(Status.COMPLETED.name).map { it.title })
        assertEquals(listOf("Gone"), dao.getEntriesByStatus(Status.DELIVERED.name).map { it.title })
    }

    // --- Flow behaviour ----------------------------------------------------

    @Test
    fun getAllEntriesOrdersByDateThenIdDescending() = runTest {
        dao.insertEntry(entry("Old", LocalDate.of(2026, 1, 1)))
        dao.insertEntry(entry("New first", LocalDate.of(2026, 6, 1)))
        dao.insertEntry(entry("New second", LocalDate.of(2026, 6, 1)))

        val titles = dao.getAllEntries().first().map { it.title }

        assertEquals(listOf("New second", "New first", "Old"), titles)
    }

    @Test
    fun getAllEntriesEmitsAgainWhenAnEntryIsInserted() = runTest {
        dao.getAllEntries().test {
            assertTrue(awaitItem().isEmpty())

            dao.insertEntry(entry("Appeared", LocalDate.of(2026, 3, 21)))

            // Room re-runs the query on table invalidation; without this the
            // Today screen would not refresh after a save.
            assertEquals(listOf("Appeared"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getEntriesByDateEmitsAgainWhenAMatchingEntryIsDeleted() = runTest {
        val id = dao.insertEntry(entry("Doomed", LocalDate.of(2026, 3, 21)))

        dao.getEntriesByDate(LocalDate.of(2026, 3, 21)).test {
            assertEquals(1, awaitItem().size)

            dao.deleteEntryById(id)

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
