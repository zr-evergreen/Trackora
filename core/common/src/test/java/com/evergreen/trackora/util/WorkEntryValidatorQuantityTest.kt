package com.evergreen.trackora.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the Persian-digit path through quantity input.
 *
 * The invariant under test is that a user typing `۱۲` ends up with the number
 * 12 in the database — not the string "۱۲", and not a validation error.
 *
 * This works partly because the JVM's digit handling is Unicode-aware, which
 * makes it easy to believe there is nothing to test. That is exactly why these
 * tests exist: the behaviour is currently inherited from `Char.isDigit` and
 * `Character.digit` rather than stated anywhere in our own code, so nothing
 * would catch a future change — swapping the filter to an ASCII range check,
 * or a hand-rolled parser — that quietly broke Persian input.
 */
class WorkEntryValidatorQuantityTest {

    // --- sanitizeQuantity preserves the user's digit system ----------------

    @Test
    fun `sanitize keeps Persian-Indic digits`() {
        assertEquals("۱۲", WorkEntryValidator.sanitizeQuantity("۱۲"))
    }

    @Test
    fun `sanitize keeps Arabic-Indic digits`() {
        assertEquals("١٢", WorkEntryValidator.sanitizeQuantity("١٢"))
    }

    @Test
    fun `sanitize still strips non-digits from Persian input`() {
        assertEquals("۱۲", WorkEntryValidator.sanitizeQuantity("۱ عدد ۲"))
    }

    @Test
    fun `sanitize strips letters and symbols from Western input`() {
        assertEquals("42", WorkEntryValidator.sanitizeQuantity("4a2!"))
    }

    // --- validateQuantity accepts both digit systems ------------------------

    @Test
    fun `Persian digits validate as a number`() {
        assertTrue(WorkEntryValidator.validateQuantity("۱۲") is ValidationResult.Valid)
    }

    @Test
    fun `Arabic-Indic digits validate as a number`() {
        assertTrue(WorkEntryValidator.validateQuantity("١٢") is ValidationResult.Valid)
    }

    @Test
    fun `Persian zero is rejected by the minimum bound, not by parsing`() {
        // The distinction matters: a parse failure would report "invalid
        // number" when the real problem is that zero is out of range.
        val result = WorkEntryValidator.validateQuantity("۰")

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            AppConstants.Errors.QUANTITY_TOO_SMALL,
            (result as ValidationResult.Invalid).message
        )
    }

    @Test
    fun `a Persian number above the maximum is rejected by the upper bound`() {
        val tooLarge = PersianDigits.toPersian((AppConstants.QUANTITY_MAX_VALUE + 1).toString())

        val result = WorkEntryValidator.validateQuantity(tooLarge)

        assertTrue(result is ValidationResult.Invalid)
        assertEquals(
            AppConstants.Errors.QUANTITY_TOO_LARGE,
            (result as ValidationResult.Invalid).message
        )
    }

    @Test
    fun `the maximum quantity is accepted in Persian digits`() {
        val atMax = PersianDigits.toPersian(AppConstants.QUANTITY_MAX_VALUE.toString())

        assertTrue(WorkEntryValidator.validateQuantity(atMax) is ValidationResult.Valid)
    }

    @Test
    fun `blank stays valid because quantity is optional`() {
        assertTrue(WorkEntryValidator.validateQuantity("") is ValidationResult.Valid)
        assertTrue(WorkEntryValidator.validateQuantity("   ") is ValidationResult.Valid)
    }

    // --- The storage invariant ---------------------------------------------

    @Test
    fun `Persian input normalises to the same Int as the Western equivalent`() {
        // This is the property the database depends on. Both spellings of the
        // same number must produce one value, or the same quantity typed on
        // two different keyboards would store differently.
        for (value in 1..2000) {
            val western = value.toString()
            val persian = PersianDigits.toPersian(western)

            assertEquals(
                "quantity $value",
                value,
                PersianDigits.toWestern(persian).toIntOrNull()
            )
        }
    }

    @Test
    fun `a Persian quantity survives sanitize then normalise then parse`() {
        // The full path the add/edit screen actually takes.
        val typed = "۱۲۳"

        val sanitized = WorkEntryValidator.sanitizeQuantity(typed)
        val stored = PersianDigits.toWestern(sanitized).toIntOrNull()

        assertEquals(123, stored)
    }
}
