package com.evergreen.trackora.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianDigitsTest {

    // --- toPersian ---------------------------------------------------------

    @Test
    fun `toPersian converts each Western digit to its Persian equivalent`() {
        assertEquals("۰۱۲۳۴۵۶۷۸۹", PersianDigits.toPersian("0123456789"))
    }

    @Test
    fun `toPersian leaves an empty string alone`() {
        assertEquals("", PersianDigits.toPersian(""))
    }

    @Test
    fun `toPersian preserves separators so formatted dates survive`() {
        assertEquals("۱۴۰۴/۰۵/۲۳", PersianDigits.toPersian("1404/05/23"))
    }

    @Test
    fun `toPersian converts only the digits in a mixed string`() {
        // A realistic entry title: Persian words, a Latin brand name, a number.
        assertEquals(
            "پروژه Trackora ۱۲ عدد",
            PersianDigits.toPersian("پروژه Trackora 12 عدد")
        )
    }

    @Test
    fun `toPersian leaves a string with no digits untouched`() {
        assertEquals("سلام دنیا", PersianDigits.toPersian("سلام دنیا"))
    }

    @Test
    fun `toPersian is unaffected by already-Persian digits`() {
        assertEquals("۱۲۳", PersianDigits.toPersian("۱۲۳"))
    }

    // --- toWestern ---------------------------------------------------------

    @Test
    fun `toWestern converts each Persian digit to its Western equivalent`() {
        assertEquals("0123456789", PersianDigits.toWestern("۰۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `toWestern also accepts Arabic-Indic digits`() {
        // Arabic keyboards are common on Iranian phones and several glyphs are
        // near-identical to the Persian ones; rejecting these would surface as
        // an inexplicable "invalid number" error.
        assertEquals("0123456789", PersianDigits.toWestern("٠١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun `toWestern handles a string mixing both Indic digit sets`() {
        assertEquals("1234", PersianDigits.toWestern("۱۲٣٤"))
    }

    @Test
    fun `toWestern leaves an empty string alone`() {
        assertEquals("", PersianDigits.toWestern(""))
    }

    @Test
    fun `toWestern preserves non-digit characters`() {
        assertEquals("1404/05/23", PersianDigits.toWestern("۱۴۰۴/۰۵/۲۳"))
    }

    @Test
    fun `toWestern is unaffected by already-Western digits`() {
        assertEquals("123", PersianDigits.toWestern("123"))
    }

    // --- Round trip --------------------------------------------------------

    @Test
    fun `every number survives a round trip in both directions`() {
        for (value in 0..2000) {
            val western = value.toString()
            assertEquals(
                western,
                PersianDigits.toWestern(PersianDigits.toPersian(western))
            )
        }
    }

    /**
     * The property that actually protects the database: whatever digit system
     * the user types in, the value stored is the same ASCII number.
     */
    @Test
    fun `typing a quantity in Persian yields the same Int as typing it in Western`() {
        assertEquals(12, PersianDigits.toWestern("۱۲").toInt())
        assertEquals(12, PersianDigits.toWestern("12").toInt())
        assertEquals(999_999, PersianDigits.toWestern("۹۹۹۹۹۹").toInt())
    }

    // --- Grouped formatting ------------------------------------------------

    @Test
    fun `format groups Persian thousands with the Arabic thousands separator`() {
        // U+066C, not a Latin comma.
        assertEquals("۱٬۲۳۴", PersianDigits.format(1234, usePersianDigits = true))
    }

    @Test
    fun `format groups English thousands with a comma`() {
        assertEquals("1,234", PersianDigits.format(1234, usePersianDigits = false))
    }

    @Test
    fun `format handles values below the grouping threshold`() {
        assertEquals("۹۹۹", PersianDigits.format(999, usePersianDigits = true))
        assertEquals("999", PersianDigits.format(999, usePersianDigits = false))
    }

    @Test
    fun `format groups multiple thousands correctly`() {
        assertEquals("1,234,567", PersianDigits.format(1_234_567, usePersianDigits = false))
        assertEquals("۱٬۲۳۴٬۵۶۷", PersianDigits.format(1_234_567, usePersianDigits = true))
    }

    @Test
    fun `format places the separator correctly on exact powers of ten`() {
        assertEquals("1,000", PersianDigits.format(1000, usePersianDigits = false))
        assertEquals("10,000", PersianDigits.format(10_000, usePersianDigits = false))
        assertEquals("100,000", PersianDigits.format(100_000, usePersianDigits = false))
        assertEquals("۱٬۰۰۰", PersianDigits.format(1000, usePersianDigits = true))
    }

    @Test
    fun `format renders zero`() {
        assertEquals("۰", PersianDigits.format(0, usePersianDigits = true))
        assertEquals("0", PersianDigits.format(0, usePersianDigits = false))
    }

    @Test
    fun `format keeps the sign outside the grouped digits for negatives`() {
        assertEquals("-1,234", PersianDigits.format(-1234, usePersianDigits = false))
        assertEquals("-۱٬۲۳۴", PersianDigits.format(-1234, usePersianDigits = true))
    }

    @Test
    fun `format does not overflow on Long MIN_VALUE`() {
        // Negating this value wraps back to itself, so the sign must be split
        // off the rendered string rather than the number.
        assertEquals(
            "-9,223,372,036,854,775,808",
            PersianDigits.format(Long.MIN_VALUE, usePersianDigits = false)
        )
    }

    // --- Ungrouped formatting ----------------------------------------------

    @Test
    fun `formatUngrouped leaves years without a separator`() {
        // A year is not a quantity: ۱٬۴۰۴ would read as a mistake.
        assertEquals("۱۴۰۴", PersianDigits.formatUngrouped(1404, usePersianDigits = true))
        assertEquals("1404", PersianDigits.formatUngrouped(1404, usePersianDigits = false))
    }

    @Test
    fun `formatUngrouped handles small numbers`() {
        assertEquals("۵", PersianDigits.formatUngrouped(5, usePersianDigits = true))
        assertEquals("۲۳", PersianDigits.formatUngrouped(23, usePersianDigits = true))
    }
}
