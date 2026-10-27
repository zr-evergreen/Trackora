package com.evergreen.trackora.export

import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.domain.model.WorkEntry
import com.evergreen.trackora.settings.CustomFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the CSV export.
 *
 * The two things that break a CSV export in the field are quoting and
 * encoding, and neither announces itself: a missing quote shifts every
 * following column silently, and a missing BOM turns an entire Persian export
 * into mojibake only once it reaches Excel. Both are covered here.
 */
class WorkEntryCsvTest {

    private fun entry(
        title: String = "Hem trousers",
        description: String? = null,
        quantity: Int? = 3,
        status: Status = Status.COMPLETED,
        date: LocalDate = LocalDate.of(2026, 8, 21),
        c1: String? = null,
        c2: String? = null,
        c3: String? = null,
    ) = WorkEntry(
        id = 1, title = title, description = description, quantity = quantity,
        status = status, date = date, customField1 = c1, customField2 = c2,
        customField3 = c3, photoUri = null
    )

    private fun rows(csv: String) =
        csv.removePrefix(WorkEntryCsv.BOM).split("\r\n").filter { it.isNotEmpty() }

    // --- Encoding -----------------------------------------------------------

    @Test
    fun `output starts with a byte order mark`() {
        // Without this Excel decodes the file as the system code page and every
        // Persian character arrives as mojibake.
        assertTrue(WorkEntryCsv.format(listOf(entry())).startsWith(WorkEntryCsv.BOM))
    }

    @Test
    fun `rows are terminated with CRLF as RFC 4180 requires`() {
        val csv = WorkEntryCsv.format(listOf(entry()))

        assertTrue(csv.endsWith("\r\n"))
        assertEquals(2, rows(csv).size) // header + one entry
    }

    @Test
    fun `Persian text survives unchanged`() {
        val csv = WorkEntryCsv.format(listOf(entry(title = "دوخت پیراهن")))

        assertTrue(csv.contains("دوخت پیراهن"))
    }

    // --- Quoting ------------------------------------------------------------

    @Test
    fun `a field containing a comma is quoted`() {
        val csv = WorkEntryCsv.format(listOf(entry(title = "Hem, cuff and press")))

        assertTrue(csv.contains("\"Hem, cuff and press\""))
    }

    @Test
    fun `embedded quotes are doubled`() {
        val csv = WorkEntryCsv.format(listOf(entry(title = """He said "urgent"""")))

        assertTrue(csv.contains("""He said ""urgent"""""))
    }

    @Test
    fun `a newline inside a description does not split the row`() {
        val csv = WorkEntryCsv.format(
            listOf(entry(description = "line one\nline two"))
        )

        // Quoted newlines stay inside the field, so splitting on CRLF still
        // yields exactly two records.
        assertEquals(2, rows(csv).size)
        assertTrue(csv.contains("\"line one\nline two\""))
    }

    @Test
    fun `leading and trailing spaces are preserved by quoting`() {
        val csv = WorkEntryCsv.format(listOf(entry(title = "  padded  ")))

        assertTrue(csv.contains("\"  padded  \""))
    }

    @Test
    fun `an ordinary field is not quoted`() {
        val csv = WorkEntryCsv.format(listOf(entry(title = "Hem trousers")))

        assertTrue(csv.contains(",Hem trousers,"))
    }

    // --- Content ------------------------------------------------------------

    @Test
    fun `dates are ISO and digits stay Western so a spreadsheet can use them`() {
        val csv = WorkEntryCsv.format(
            listOf(entry(date = LocalDate.of(2026, 8, 21), quantity = 1234))
        )

        assertTrue(csv.contains("2026-08-21"))
        assertTrue(csv.contains("1234"))
        assertTrue("Persian digits would be text, not numbers, in a spreadsheet",
            !csv.contains("۱۲۳۴"))
    }

    @Test
    fun `null optional fields become empty cells, not the word null`() {
        val csv = WorkEntryCsv.format(
            listOf(entry(description = null, quantity = null, c1 = null, c2 = null, c3 = null))
        )

        assertTrue(!csv.contains("null"))
        // date,title,,,status,,, -> trailing empties preserved
        assertTrue(rows(csv)[1].endsWith(",,,"))
    }

    @Test
    fun `the header uses the user's own custom field names`() {
        val csv = WorkEntryCsv.format(
            entries = listOf(entry()),
            customFields = CustomFields("نام مشتری", "کد سفارش", "")
        )

        val header = rows(csv).first()
        assertTrue(header.contains("نام مشتری"))
        assertTrue(header.contains("کد سفارش"))
        // Blank name falls back to the positional label rather than an empty column.
        assertTrue(header.contains("Custom field 3"))
    }

    @Test
    fun `status is written with the supplied localised label`() {
        val csv = WorkEntryCsv.format(
            entries = listOf(entry(status = Status.DELIVERED)),
            headers = WorkEntryCsv.Headers(delivered = "تحویل‌شده")
        )

        assertTrue(csv.contains("تحویل‌شده"))
    }

    @Test
    fun `every entry produces exactly one row`() {
        val csv = WorkEntryCsv.format((1..25).map { entry(title = "Job $it") })

        assertEquals(26, rows(csv).size) // header + 25
    }

    @Test
    fun `an empty list still produces a header`() {
        // The view model refuses to export nothing, but the formatter should
        // not be the thing that decides that.
        assertEquals(1, rows(WorkEntryCsv.format(emptyList())).size)
    }
}
