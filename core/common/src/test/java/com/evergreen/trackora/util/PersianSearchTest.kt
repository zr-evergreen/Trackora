package com.evergreen.trackora.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for search folding.
 *
 * Each case here is a way a Persian user can type something that is visually
 * identical to the stored text and still fail to match under a plain
 * `contains()` — which reads as a broken app rather than a near miss.
 */
class PersianSearchTest {

    // --- Arabic vs Persian letterforms --------------------------------------

    @Test
    fun `Arabic kaf finds text stored with Persian keheh`() {
        // ك (U+0643) vs ک (U+06A9): near-identical on screen, different letters.
        assertTrue(PersianSearch.matches("کار جدید", "كار"))
    }

    @Test
    fun `Persian keheh finds text stored with Arabic kaf`() {
        assertTrue(PersianSearch.matches("كار جدید", "کار"))
    }

    @Test
    fun `Arabic yeh finds text stored with Farsi yeh`() {
        // ي (U+064A) vs ی (U+06CC).
        assertTrue(PersianSearch.matches("پیراهن", "پيراهن"))
    }

    @Test
    fun `alef with madda matches plain alef`() {
        assertTrue(PersianSearch.matches("آستین", "استین"))
    }

    @Test
    fun `teh marbuta matches heh`() {
        assertTrue(PersianSearch.matches("پارچة", "پارچه"))
    }

    // --- Zero-width non-joiner ----------------------------------------------

    @Test
    fun `a query without the half-space finds text written with one`() {
        // «تکمیل‌شده» carries U+200C; a user typing quickly writes it without.
        assertTrue(PersianSearch.matches("تکمیل‌شده", "تکمیلشده"))
    }

    @Test
    fun `a query with the half-space finds text written without one`() {
        assertTrue(PersianSearch.matches("تکمیلشده", "تکمیل‌شده"))
    }

    // --- Digits --------------------------------------------------------------

    @Test
    fun `Persian digits find a quantity stored in Western digits`() {
        // The user's keyboard produces ۱۲; the record holds 12.
        assertTrue(PersianSearch.matches("order 12", "۱۲"))
    }

    @Test
    fun `Western digits find text written with Persian digits`() {
        assertTrue(PersianSearch.matches("سفارش ۴۲", "42"))
    }

    // --- Latin behaviour is preserved ---------------------------------------

    @Test
    fun `search is case insensitive for Latin text`() {
        assertTrue(PersianSearch.matches("Nike Order", "nike"))
        assertTrue(PersianSearch.matches("nike order", "NIKE"))
    }

    @Test
    fun `a mixed Persian and Latin entry is searchable from either script`() {
        val title = "سفارش Nike ۴۲"

        assertTrue(PersianSearch.matches(title, "سفارش"))
        assertTrue(PersianSearch.matches(title, "nike"))
        assertTrue(PersianSearch.matches(title, "42"))
    }

    // --- Non-matches still fail ---------------------------------------------

    @Test
    fun `folding does not make unrelated words match`() {
        // Guards against over-aggressive folding turning search into noise.
        assertFalse(PersianSearch.matches("پیراهن", "شلوار"))
        assertFalse(PersianSearch.matches("Nike order", "adidas"))
    }

    @Test
    fun `a null or empty field never matches a real query`() {
        assertFalse(PersianSearch.matches(null, "کار"))
        assertFalse(PersianSearch.matches("", "کار"))
    }

    // --- Empty query ---------------------------------------------------------

    @Test
    fun `a blank query matches everything so callers need no special case`() {
        assertTrue(PersianSearch.matches("anything", ""))
        assertTrue(PersianSearch.matches("anything", "   "))
        assertTrue(PersianSearch.matches(null, ""))
    }

    // --- normalize ------------------------------------------------------------

    @Test
    fun `normalize folds letterforms, strips the half-space and westernises digits`() {
        assertEquals("کار ۱۲".let { PersianSearch.normalize(it) }, "کار 12")
        assertEquals("تکمیلشده", PersianSearch.normalize("تکمیل‌شده"))
        assertEquals("کار", PersianSearch.normalize("  كار  "))
    }

    @Test
    fun `normalize leaves ordinary Latin text alone apart from case`() {
        assertEquals("hem trousers", PersianSearch.normalize("Hem Trousers"))
    }
}
