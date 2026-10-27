package com.evergreen.trackora.feature.addedit

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

/**
 * The Gregorian picker moved an entry's date back a day in Iran: opening it on
 * 27 Oct and pressing Select saved 26 Oct. Each test pins the device zone,
 * because the defect only exists away from UTC.
 */
class DatePickerDatesTest {

    private val originalZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    private fun inZone(id: String, block: () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        block()
    }

    @Test
    fun `the picker is opened on UTC midnight of the entry's date in Tehran`() = inZone("Asia/Tehran") {
        val millis = DatePickerDates.toPickerMillis(LocalDate.of(2026, 10, 27))

        // Previously 2026-10-26T20:30:00Z, which the picker shows as 26 Oct.
        assertEquals(Instant.parse("2026-10-27T00:00:00Z").toEpochMilli(), millis)
    }

    @Test
    fun `confirming the picker without a change keeps the date in Tehran`() = inZone("Asia/Tehran") {
        val date = LocalDate.of(2026, 10, 27)

        assertEquals(date, DatePickerDates.fromPickerMillis(DatePickerDates.toPickerMillis(date)))
    }

    @Test
    fun `a day picked west of UTC is read back as that day`() = inZone("America/Los_Angeles") {
        // The picker reports the chosen day as UTC midnight; through the device
        // zone that would read as the evening before.
        val picked = Instant.parse("2026-10-22T00:00:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 10, 22), DatePickerDates.fromPickerMillis(picked))
    }

    @Test
    fun `dates round-trip unchanged in every zone, across a year`() {
        for (zone in listOf("Asia/Tehran", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC")) {
            inZone(zone) {
                var date = LocalDate.of(2026, 1, 1)
                while (date.year == 2026) {
                    assertEquals(
                        "round trip of $date in $zone",
                        date,
                        DatePickerDates.fromPickerMillis(DatePickerDates.toPickerMillis(date))
                    )
                    date = date.plusDays(1)
                }
            }
        }
    }
}
