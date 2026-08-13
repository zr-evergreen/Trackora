package com.evergreen.trackora.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the Jalali (Persian Solar) calendar conversion.
 *
 * Every date the user sees is rendered through this object, and a conversion
 * that is off by a single day silently files work under the wrong day forever —
 * there is no other layer that would notice. The database stores Gregorian
 * LocalDate, so a bug here is invisible in the data and only shows up as the
 * user disagreeing with the app about what day it is.
 *
 * The tests are deliberately weighted towards *invariants* (round-trip,
 * monotonicity, leap-year/day-count agreement) rather than long tables of
 * hand-copied date pairs. Invariants hold regardless of which astronomical
 * Nowruz table you consult, and they exercise thousands of dates instead of a
 * handful, so they catch drift that spot checks walk straight past.
 */
class JalaliCalendarTest {

    // --- Fixed anchors -----------------------------------------------------

    /**
     * The Unix epoch is the least controversial anchor available: 1 January
     * 1970 is 11 Dey 1348 in every published Jalali table.
     */
    @Test
    fun `unix epoch converts to 11 Dey 1348`() {
        val jalali = JalaliCalendar.gregorianToJalali(LocalDate.of(1970, 1, 1))

        assertEquals(1348, jalali.year)
        assertEquals(10, jalali.month)
        assertEquals(11, jalali.day)
    }

    @Test
    fun `11 Dey 1348 converts back to the unix epoch`() {
        val gregorian = JalaliCalendar.jalaliToGregorian(JalaliCalendar.JalaliDate(1348, 10, 11))

        assertEquals(LocalDate.of(1970, 1, 1), gregorian)
    }

    /** Nowruz 1400 fell on 21 March 2021. */
    @Test
    fun `Nowruz 1400 maps to 21 March 2021`() {
        assertEquals(
            LocalDate.of(2021, 3, 21),
            JalaliCalendar.jalaliToGregorian(JalaliCalendar.JalaliDate(1400, 1, 1))
        )
        assertEquals(
            JalaliCalendar.JalaliDate(1400, 1, 1),
            JalaliCalendar.gregorianToJalali(LocalDate.of(2021, 3, 21))
        )
    }

    /** The day before Nowruz 1400 is the last day of Esfand 1399. */
    @Test
    fun `day before Nowruz 1400 is the last day of Esfand 1399`() {
        val jalali = JalaliCalendar.gregorianToJalali(LocalDate.of(2021, 3, 20))

        assertEquals(1399, jalali.year)
        assertEquals(12, jalali.month)
        assertEquals(JalaliCalendar.getDaysInJalaliMonth(1399, 12), jalali.day)
    }

    /**
     * The implementation uses the arithmetic 33-year leap cycle, which is an
     * approximation of the astronomically determined Iranian calendar. The two
     * are known to diverge eventually, so this pins the decade the app actually
     * ships into against the published Nowruz dates. If a future year starts
     * failing here, the arithmetic rule has drifted and needs a correction
     * table — the round-trip tests would not notice, because the calendar would
     * still be perfectly self-consistent while disagreeing with every wall
     * calendar in Iran.
     */
    @Test
    fun `Nowruz matches the official Iranian calendar for 1399 to 1408`() {
        val officialNowruz = mapOf(
            1399 to LocalDate.of(2020, 3, 20),
            1400 to LocalDate.of(2021, 3, 21),
            1401 to LocalDate.of(2022, 3, 21),
            1402 to LocalDate.of(2023, 3, 21),
            1403 to LocalDate.of(2024, 3, 20),
            1404 to LocalDate.of(2025, 3, 21),
            1405 to LocalDate.of(2026, 3, 21),
            1406 to LocalDate.of(2027, 3, 21),
            1407 to LocalDate.of(2028, 3, 20),
            1408 to LocalDate.of(2029, 3, 20),
        )

        officialNowruz.forEach { (jalaliYear, gregorian) ->
            assertEquals(
                "Nowruz $jalaliYear",
                gregorian,
                JalaliCalendar.jalaliToGregorian(JalaliCalendar.JalaliDate(jalaliYear, 1, 1))
            )
        }
    }

    // --- Round trip --------------------------------------------------------

    /**
     * Converting to Jalali and back must be the identity. Run across ~27 years
     * of consecutive days so that every month length, every leap year and every
     * century boundary in the supported range is covered.
     */
    @Test
    fun `gregorian to jalali and back is the identity for ten thousand consecutive days`() {
        var date = LocalDate.of(2000, 1, 1)
        repeat(10_000) {
            val roundTripped = JalaliCalendar.jalaliToGregorian(
                JalaliCalendar.gregorianToJalali(date)
            )
            assertEquals("round trip failed for $date", date, roundTripped)
            date = date.plusDays(1)
        }
    }

    @Test
    fun `round trip holds across the 1970 epoch boundary`() {
        var date = LocalDate.of(1969, 1, 1)
        repeat(800) {
            val roundTripped = JalaliCalendar.jalaliToGregorian(
                JalaliCalendar.gregorianToJalali(date)
            )
            assertEquals("round trip failed for $date", date, roundTripped)
            date = date.plusDays(1)
        }
    }

    /**
     * Consecutive Gregorian days must produce consecutive Jalali days: no
     * repeats and no gaps. A conversion can round-trip correctly and still skip
     * a day at a month or year boundary, which this catches and the round trip
     * does not.
     */
    @Test
    fun `consecutive gregorian days produce strictly consecutive jalali days`() {
        var date = LocalDate.of(2015, 1, 1)
        var previous = JalaliCalendar.gregorianToJalali(date)

        repeat(5_000) {
            date = date.plusDays(1)
            val current = JalaliCalendar.gregorianToJalali(date)

            val advancedWithinMonth =
                current.year == previous.year &&
                    current.month == previous.month &&
                    current.day == previous.day + 1

            val rolledToNextMonth =
                current.year == previous.year &&
                    current.month == previous.month + 1 &&
                    current.day == 1 &&
                    previous.day == JalaliCalendar.getDaysInJalaliMonth(previous.year, previous.month)

            val rolledToNextYear =
                current.year == previous.year + 1 &&
                    current.month == 1 &&
                    current.day == 1 &&
                    previous.month == 12 &&
                    previous.day == JalaliCalendar.getDaysInJalaliMonth(previous.year, 12)

            assertTrue(
                "non-consecutive Jalali dates at $date: $previous -> $current",
                advancedWithinMonth || rolledToNextMonth || rolledToNextYear
            )
            previous = current
        }
    }

    // --- Month lengths and leap years --------------------------------------

    @Test
    fun `first six months have 31 days and months seven to eleven have 30`() {
        (1..6).forEach { month ->
            assertEquals("month $month", 31, JalaliCalendar.getDaysInJalaliMonth(1400, month))
        }
        (7..11).forEach { month ->
            assertEquals("month $month", 30, JalaliCalendar.getDaysInJalaliMonth(1400, month))
        }
    }

    @Test
    fun `Esfand has 30 days in a leap year and 29 otherwise`() {
        val leap = (1390..1420).first { JalaliCalendar.isLeapYear(it) }
        val common = (1390..1420).first { !JalaliCalendar.isLeapYear(it) }

        assertEquals(30, JalaliCalendar.getDaysInJalaliMonth(leap, 12))
        assertEquals(29, JalaliCalendar.getDaysInJalaliMonth(common, 12))
    }

    /**
     * `isLeapYear` and the conversion functions are two independent pieces of
     * arithmetic that must agree. If they disagree, the date picker offers a
     * 30 Esfand that the converter refuses to accept (or hides a real day).
     * Measuring the true year length through the converter is the only way to
     * check one against the other.
     */
    @Test
    fun `isLeapYear agrees with the year length produced by the converter`() {
        (1380..1420).forEach { year ->
            val nowruz = JalaliCalendar.jalaliToGregorian(JalaliCalendar.JalaliDate(year, 1, 1))
            val nextNowruz =
                JalaliCalendar.jalaliToGregorian(JalaliCalendar.JalaliDate(year + 1, 1, 1))
            val lengthInDays = nextNowruz.toEpochDay() - nowruz.toEpochDay()

            val expected = if (JalaliCalendar.isLeapYear(year)) 366L else 365L
            assertEquals("year length disagrees for $year", expected, lengthInDays)
        }
    }

    /**
     * The last day of Esfand must survive a round trip. In a leap year that is
     * 30 Esfand — the day most likely to be dropped by an off-by-one.
     */
    @Test
    fun `last day of Esfand round trips for every year in range`() {
        (1380..1420).forEach { year ->
            val lastDay = JalaliCalendar.getDaysInJalaliMonth(year, 12)
            val jalali = JalaliCalendar.JalaliDate(year, 12, lastDay)

            val roundTripped = JalaliCalendar.gregorianToJalali(
                JalaliCalendar.jalaliToGregorian(jalali)
            )
            assertEquals("last day of Esfand $year", jalali, roundTripped)
        }
    }

    // --- Validation --------------------------------------------------------

    @Test
    fun `isValidJalaliDate accepts ordinary dates`() {
        assertTrue(JalaliCalendar.isValidJalaliDate(1400, 1, 1))
        assertTrue(JalaliCalendar.isValidJalaliDate(1400, 6, 31))
        assertTrue(JalaliCalendar.isValidJalaliDate(1400, 7, 30))
    }

    @Test
    fun `isValidJalaliDate rejects out of range months and days`() {
        assertFalse("month 0", JalaliCalendar.isValidJalaliDate(1400, 0, 1))
        assertFalse("month 13", JalaliCalendar.isValidJalaliDate(1400, 13, 1))
        assertFalse("day 0", JalaliCalendar.isValidJalaliDate(1400, 1, 0))
        assertFalse("32nd of a 31 day month", JalaliCalendar.isValidJalaliDate(1400, 1, 32))
        assertFalse("31st of a 30 day month", JalaliCalendar.isValidJalaliDate(1400, 7, 31))
        assertFalse("year 0", JalaliCalendar.isValidJalaliDate(0, 1, 1))
    }

    @Test
    fun `isValidJalaliDate accepts 30 Esfand only in a leap year`() {
        val leap = (1390..1420).first { JalaliCalendar.isLeapYear(it) }
        val common = (1390..1420).first { !JalaliCalendar.isLeapYear(it) }

        assertTrue(JalaliCalendar.isValidJalaliDate(leap, 12, 30))
        assertFalse(JalaliCalendar.isValidJalaliDate(common, 12, 30))
    }

    // --- Formatting --------------------------------------------------------

    @Test
    fun `month names are returned in both languages and blank when out of range`() {
        assertEquals("فروردین", JalaliCalendar.getJalaliMonthName(1))
        assertEquals("اسفند", JalaliCalendar.getJalaliMonthName(12))
        assertEquals("Farvardin", JalaliCalendar.getJalaliMonthNameEn(1))
        assertEquals("Esfand", JalaliCalendar.getJalaliMonthNameEn(12))

        assertEquals("", JalaliCalendar.getJalaliMonthName(0))
        assertEquals("", JalaliCalendar.getJalaliMonthName(13))
        assertEquals("", JalaliCalendar.getJalaliMonthNameEn(13))
    }

    @Test
    fun `formatJalaliDate uses Persian names by default and English on request`() {
        val date = JalaliCalendar.JalaliDate(1400, 1, 15)

        assertEquals("15 فروردین 1400", JalaliCalendar.formatJalaliDate(date))
        assertEquals(
            "15 Farvardin 1400",
            JalaliCalendar.formatJalaliDate(date, usePersianNames = false)
        )
    }

    @Test
    fun `JalaliDate toString is the slash separated form`() {
        assertEquals("1400/1/15", JalaliCalendar.JalaliDate(1400, 1, 15).toString())
    }
}
