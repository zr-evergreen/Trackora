package com.evergreen.trackora.feature.allwork

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
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

        val sections = state.sections

        assertEquals(2, sections.size)
        assertEquals(newer, sections[0].date)
        assertEquals(older, sections[1].date)
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
        assertEquals(1, state.sections.size)
        assertEquals(LocalDate.of(2026, 8, 29), state.sections.first().date)
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
