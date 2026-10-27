package com.evergreen.trackora.feature.addedit

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Conversions between a calendar date and the millis the Material 3 date
 * picker works in.
 *
 * The picker treats every date as UTC midnight, both for its initial
 * selection and for what it reports back. Converting through the device's
 * zone instead shifts the day in any zone that is not UTC: in Tehran (+03:30)
 * local midnight is 20:30 UTC on the previous day, so the picker opened on
 * the day before the entry's date and pressing Select without touching
 * anything saved that earlier day. West of UTC the error moves to the
 * reading side instead.
 *
 * Both directions therefore use UTC, whatever zone the device is in.
 */
internal object DatePickerDates {

    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
