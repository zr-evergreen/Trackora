package com.evergreen.trackora.feature.allwork

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.ui.text.calendarMonthKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the derived state behind All Work.
 *
 * Filtering, searching and day grouping are computed from the entry list
 * rather than stored, so these are the rules that decide what the user sees.
 */
class AllWorkUiStateTest {

    private fun entry(
        id: Long,
        title: String = "Job $id",
        status: Status = Status.IN_PROGRESS,
        date: LocalDate = LocalDate.of(2026, 8, 29),
        quantity: Int? = null,
        customer: String? = null,
    ) = WorkEntry(
        id = id, title = title, status = status, date = date,
        quantity = quantity, customField1 = customer
    )

    // --- Search ---------------------------------------------------------------

    @Test
    fun `search matches the title`() {
        val state = AllWorkUiState(
            entries = listOf(entry(1, "Hem trousers"), entry(2, "Repair zip")),
            query = "hem"
        )

        assertEquals(listOf(1L), state.filteredEntries.map { it.id })
    }

    @Test
    fun `search matches the customer field`() {
        // Custom field 1 is where the customer name lives in practice, so
        // "everything the user typed" has to include it.
        val state = AllWorkUiState(
            entries = listOf(
                entry(1, customer = "خانم احمدی"),
                entry(2, customer = "آقای رضایی")
            ),
            query = "احمدی"
        )

        assertEquals(listOf(1L), state.filteredEntries.map { it.id })
    }

    @Test
    fun `search matches a quantity so an order can be found by piece count`() {
        val state = AllWorkUiState(
            entries = listOf(entry(1, quantity = 12), entry(2, quantity = 3)),
            query = "12"
        )

        assertEquals(listOf(1L), state.filteredEntries.map { it.id })
    }

    @Test
    fun `search folds Arabic letterforms onto Persian ones`() {
        // The stored name uses Persian keheh; the query uses Arabic kaf.
        val state = AllWorkUiState(
            entries = listOf(entry(1, customer = "کریمی")),
            query = "كريمي"
        )

        assertEquals(1, state.filteredEntries.size)
    }

    @Test
    fun `a blank query leaves the list untouched`() {
        val entries = listOf(entry(1), entry(2), entry(3))

        assertEquals(3, AllWorkUiState(entries = entries, query = "   ").filteredEntries.size)
    }

    // --- Search combined with the status filter -------------------------------

    @Test
    fun `search and status filter both apply`() {
        val state = AllWorkUiState(
            entries = listOf(
                entry(1, "Hem trousers", status = Status.COMPLETED),
                entry(2, "Hem skirt", status = Status.IN_PROGRESS)
            ),
            filter = Status.COMPLETED,
            query = "hem"
        )

        assertEquals(listOf(1L), state.filteredEntries.map { it.id })
    }

    // --- Day grouping ---------------------------------------------------------

    @Test
    fun `entries group into days, newest first`() {
        val older = LocalDate.of(2026, 8, 27)
        val newer = LocalDate.of(2026, 8, 29)
        val state = AllWorkUiState(
            entries = listOf(
                entry(1, date = older),
                entry(2, date = newer),
                entry(3, date = older)
            )
        )

        val sections = state.sections(today = newer, monthKeyOf = gregorianMonth)

        assertEquals(2, sections.size)
        assertEquals(newer, (sections[0] as WorkSection.Day).date)
        assertEquals(older, (sections[1] as WorkSection.Day).date)
        assertEquals(2, sections[1].entries.size)
    }

    @Test
    fun `grouping is recomputed against the search, not the whole list`() {
        val state = AllWorkUiState(
            entries = listOf(
                entry(1, "Hem trousers", date = LocalDate.of(2026, 8, 29)),
                entry(2, "Repair zip", date = LocalDate.of(2026, 8, 27))
            ),
            query = "hem"
        )

        // The day that holds no match must not appear as an empty heading.
        val sections = state.sections(today = LocalDate.of(2026, 8, 29), monthKeyOf = gregorianMonth)
        assertEquals(1, sections.size)
        assertEquals(LocalDate.of(2026, 8, 29), (sections.first() as WorkSection.Day).date)
    }

    // --- Older work, by month ---------------------------------------------------

    private val gregorianMonth: (LocalDate) -> Int = { calendarMonthKey(it, jalali = false) }
    private val jalaliMonth: (LocalDate) -> Int = { calendarMonthKey(it, jalali = true) }

    @Test
    fun `the last seven days keep a section each and older work is gathered by month`() {
        val today = LocalDate.of(2026, 10, 27)
        val state = AllWorkUiState(
            entries = listOf(
                entry(1, date = today),
                entry(2, date = today.minusDays(6)),
                entry(3, date = today.minusDays(7)),
                entry(4, date = LocalDate.of(2026, 9, 3)),
                entry(5, date = LocalDate.of(2026, 9, 28))
            )
        )

        val sections = state.sections(today, gregorianMonth)

        assertEquals(
            listOf("day-2026-10-27", "day-2026-10-21", "month-${gregorianMonth(today)}", "month-${gregorianMonth(LocalDate.of(2026, 9, 1))}"),
            sections.map { it.key }
        )
        val september = sections.last() as WorkSection.Month
        // Newest first inside a month, and the heading is named by its latest date.
        assertEquals(listOf(5L, 4L), september.entries.map { it.id })
        assertEquals(LocalDate.of(2026, 9, 28), september.latest)
    }

    @Test
    fun `months follow the reader's calendar, so a Jalali month boundary splits October`() {
        // 25 October 2026 is 3 Aban 1405; 10 October is 18 Mehr. One Gregorian
        // month, two Jalali ones.
        val today = LocalDate.of(2026, 11, 30)
        val state = AllWorkUiState(
            entries = listOf(entry(1, date = LocalDate.of(2026, 10, 25)), entry(2, date = LocalDate.of(2026, 10, 10)))
        )

        assertEquals(1, state.sections(today, gregorianMonth).size)
        assertEquals(2, state.sections(today, jalaliMonth).size)
    }

    @Test
    fun `a section's items add up its entries, treating no quantity as none`() {
        val day = LocalDate.of(2026, 8, 29)
        val state = AllWorkUiState(
            entries = listOf(entry(1, date = day, quantity = 4), entry(2, date = day), entry(3, date = day, quantity = 10))
        )

        assertEquals(14, state.sections(day, gregorianMonth).single().items)
    }

    // --- Counts behind each filter ------------------------------------------------

    @Test
    fun `status counts follow the search but not the selected filter`() {
        val state = AllWorkUiState(
            entries = listOf(
                entry(1, "Hem trousers", status = Status.IN_PROGRESS),
                entry(2, "Hem skirt", status = Status.COMPLETED),
                entry(3, "Hem dress", status = Status.COMPLETED),
                entry(4, "Repair zip", status = Status.DELIVERED)
            ),
            query = "hem",
            filter = Status.IN_PROGRESS
        )

        assertEquals(mapOf(Status.IN_PROGRESS to 1, Status.COMPLETED to 2), state.countsByStatus)
        assertEquals(3, state.matchingCount)
        assertEquals(1, state.filteredEntries.size)
    }

    @Test
    fun `the list is narrowed by a filter or a search, not by nothing`() {
        assertFalse(AllWorkUiState(entries = listOf(entry(1))).isNarrowed)
        assertTrue(AllWorkUiState(entries = listOf(entry(1)), filter = Status.DELIVERED).isNarrowed)
        assertTrue(AllWorkUiState(entries = listOf(entry(1)), query = "x").isNarrowed)
    }

    // --- Distinguishing empty from no-match -----------------------------------

    @Test
    fun `a search that matches nothing is not the same as an empty log`() {
        val state = AllWorkUiState(entries = listOf(entry(1, "Hem trousers")), query = "zzz")

        // Drives "try another word" rather than "add your first job".
        assertTrue(state.hasNoMatches)
        assertTrue(state.isSearching)
    }

    @Test
    fun `an empty log is not reported as a failed search`() {
        val state = AllWorkUiState(entries = emptyList(), query = "")

        assertFalse(state.hasNoMatches)
        assertFalse(state.isSearching)
    }

    @Test
    fun `a filter hiding everything also counts as no matches`() {
        val state = AllWorkUiState(
            entries = listOf(entry(1, status = Status.IN_PROGRESS)),
            filter = Status.DELIVERED
        )

        assertTrue(state.hasNoMatches)
    }
}
