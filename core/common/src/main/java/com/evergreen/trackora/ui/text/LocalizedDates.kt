package com.evergreen.trackora.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.evergreen.trackora.common.R
import com.evergreen.trackora.util.JalaliCalendar
import com.evergreen.trackora.util.PersianDigits
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Locale-appropriate date rendering.
 *
 * Persian users get the Jalali calendar with Persian month names and Persian
 * digits; English users get the Gregorian date. This is not a formatting
 * preference — ۲۳ مرداد ۱۴۰۴ and August 14 2025 are the same day, and showing
 * an Iranian user the Gregorian date is showing them a date they would have to
 * convert in their head before it meant anything.
 *
 * The screens previously each decided this for themselves, which is why the
 * Today screen showed a Gregorian date under the Persian locale while the
 * add/edit screen showed a Jalali one.
 */

/**
 * Renders [date] in the calendar system the active locale expects.
 *
 * @param includeWeekday prefixes the weekday name — Persian weekday names for
 *   the Persian locale, where the week runs Saturday to Friday.
 */
@Composable
@ReadOnlyComposable
fun localizedDate(date: LocalDate, includeWeekday: Boolean = false): String {
    val locale = LocalConfiguration.current.locales[0]

    return if (locale.language == "fa") {
        val jalali = JalaliCalendar.gregorianToJalali(date)
        val day = PersianDigits.toPersian(jalali.day.toString())
        val month = JalaliCalendar.getJalaliMonthName(jalali.month)
        val year = PersianDigits.toPersian(jalali.year.toString())
        val body = "$day $month $year"

        if (includeWeekday) {
            // Persian uses its own comma (U+060C) and it leans the other way.
            "${JalaliCalendar.getPersianWeekdayName(date)}، $body"
        } else {
            body
        }
    } else {
        val pattern = if (includeWeekday) "EEEE, MMM d, yyyy" else "MMM d, yyyy"
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

/**
 * Renders [date] as a relative label where one reads more naturally than a
 * full date — امروز rather than ۲۳ مرداد ۱۴۰۴ — and falls back to
 * [localizedDate] once the distance stops being useful.
 *
 * Iranian apps lean on these labels heavily; an app that always spells out the
 * full date reads as stiff.
 *
 * @param today injected rather than read from the clock so this is testable
 *   and so a long-lived composition cannot keep showing a stale "امروز".
 */
@Composable
@ReadOnlyComposable
fun localizedRelativeDate(date: LocalDate, today: LocalDate): String {
    val daysBetween = ChronoUnit.DAYS.between(today, date)

    return when {
        daysBetween == 0L -> stringResource(R.string.date_today)
        daysBetween == -1L -> stringResource(R.string.date_yesterday)
        daysBetween == 1L -> stringResource(R.string.date_tomorrow)

        // Beyond a week the count stops being easier to read than the date
        // itself — "۲۳ روز پیش" makes the reader do arithmetic.
        daysBetween in -7L..-2L -> {
            val days = -daysBetween.toInt()
            pluralStringResource(R.plurals.date_days_ago, days, days)
        }

        daysBetween in 2L..7L -> {
            val days = daysBetween.toInt()
            pluralStringResource(R.plurals.date_in_days, days, days)
        }

        else -> localizedDate(date)
    }
}

/**
 * A span of days, written once rather than as two full dates: "Oct 21 – 27,
 * 2026", or «۲۹ مهر – ۵ آبان ۱۴۰۵» in the Jalali calendar. The month and year
 * are only repeated where the span crosses them.
 */
@Composable
@ReadOnlyComposable
fun localizedDateRange(start: LocalDate, end: LocalDate): String {
    if (start == end) return localizedDate(end)
    val locale = LocalConfiguration.current.locales[0]

    return if (locale.language == "fa") {
        val from = JalaliCalendar.gregorianToJalali(start)
        val to = JalaliCalendar.gregorianToJalali(end)
        fun day(value: Int) = PersianDigits.toPersian(value.toString())
        val toPart = "${day(to.day)} ${JalaliCalendar.getJalaliMonthName(to.month)} ${day(to.year)}"
        val fromPart = when {
            from.year != to.year ->
                "${day(from.day)} ${JalaliCalendar.getJalaliMonthName(from.month)} ${day(from.year)}"
            from.month != to.month -> "${day(from.day)} ${JalaliCalendar.getJalaliMonthName(from.month)}"
            else -> day(from.day)
        }
        "$fromPart – $toPart"
    } else {
        val fromPattern = when {
            start.year != end.year -> "MMM d, yyyy"
            else -> "MMM d"
        }
        val toPattern = if (start.month == end.month && start.year == end.year) "d, yyyy" else "MMM d, yyyy"
        "${start.format(DateTimeFormatter.ofPattern(fromPattern, locale))} – " +
            end.format(DateTimeFormatter.ofPattern(toPattern, locale))
    }
}

/**
 * The shortest recognisable name of [date]'s weekday, for chart axes: "Mon",
 * or the Persian single-letter form — ش ی د س چ پ ج — that Iranian calendars
 * print above their columns.
 */
@Composable
@ReadOnlyComposable
fun localizedWeekdayShort(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (locale.language == "fa") {
        PERSIAN_WEEKDAY_INITIALS[JalaliCalendar.persianWeekdayIndex(date)]
    } else {
        date.format(DateTimeFormatter.ofPattern("EEE", locale))
    }
}

/** [date]'s day of the month in the locale's calendar and digits: "27", or «۵» for ۵ آبان. */
@Composable
@ReadOnlyComposable
fun localizedDayOfMonth(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (locale.language == "fa") {
        PersianDigits.toPersian(JalaliCalendar.gregorianToJalali(date).day.toString())
    } else {
        date.dayOfMonth.toString()
    }
}

/** The month [date] falls in, with its year: "October 2026", or «آبان ۱۴۰۵». */
@Composable
@ReadOnlyComposable
fun localizedMonthTitle(date: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (locale.language == "fa") {
        val jalali = JalaliCalendar.gregorianToJalali(date)
        "${JalaliCalendar.getJalaliMonthName(jalali.month)} ${PersianDigits.toPersian(jalali.year.toString())}"
    } else {
        date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    }
}

/**
 * Identifies the calendar month [date] falls in, in the calendar the user
 * reads: Jalali months for Persian, Gregorian otherwise. Two dates share a
 * month exactly when their keys are equal. ۵ آبان and ۲۹ مهر are different
 * months even though both fall in October.
 */
fun calendarMonthKey(date: LocalDate, jalali: Boolean): Int =
    if (jalali) {
        val jalaliDate = JalaliCalendar.gregorianToJalali(date)
        jalaliDate.year * 12 + jalaliDate.month - 1
    } else {
        date.year * 12 + date.monthValue - 1
    }

private val PERSIAN_WEEKDAY_INITIALS = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")
