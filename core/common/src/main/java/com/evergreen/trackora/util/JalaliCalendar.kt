package com.evergreen.trackora.util

import java.time.LocalDate
import java.time.ZoneId
import java.util.*

/**
 * Utility class for Jalali (Persian Solar) calendar conversions.
 * Converts between Gregorian (LocalDate) and Jalali calendar dates.
 */
object JalaliCalendar {
    
    /**
     * Represents a Jalali date (Persian Solar calendar).
     */
    data class JalaliDate(
        val year: Int,
        val month: Int,
        val day: Int
    ) {
        override fun toString(): String = "$year/$month/$day"
    }
    
    /**
     * Convert Gregorian LocalDate to Jalali date.
     */
    fun gregorianToJalali(gregorianDate: LocalDate): JalaliDate {
        val calendar = Calendar.getInstance()
        calendar.set(gregorianDate.year, gregorianDate.monthValue - 1, gregorianDate.dayOfMonth)
        return gregorianToJalali(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    /**
     * Convert Jalali date to Gregorian LocalDate.
     */
    fun jalaliToGregorian(jalaliDate: JalaliDate): LocalDate {
        val (gy, gm, gd) = jalaliToGregorian(jalaliDate.year, jalaliDate.month, jalaliDate.day)
        return LocalDate.of(gy, gm, gd)
    }
    
    /**
     * Get today's date in Jalali calendar.
     */
    fun todayJalali(): JalaliDate = gregorianToJalali(LocalDate.now())
    
    /**
     * Check if a Jalali date is valid.
     */
    fun isValidJalaliDate(year: Int, month: Int, day: Int): Boolean {
        if (year < 1 || month < 1 || month > 12 || day < 1) return false
        val daysInMonth = getDaysInJalaliMonth(year, month)
        return day <= daysInMonth
    }
    
    /**
     * Get number of days in a Jalali month.
     */
    fun getDaysInJalaliMonth(year: Int, month: Int): Int {
        return if (month <= 6) 31
        else if (month <= 11) 30
        else if (isLeapYear(year)) 30
        else 29
    }
    
    /**
     * Check if a Jalali year is a leap year.
     */
    fun isLeapYear(year: Int): Boolean {
        val ary = arrayOf(1, 5, 9, 13, 17, 22, 26, 30)
        val b = year % 33
        return ary.contains(b)
    }
    
    /**
     * Get Jalali month names in Persian.
     */
    fun getJalaliMonthName(month: Int): String {
        val months = arrayOf(
            "", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        return if (month in 1..12) months[month] else ""
    }
    
    /**
     * Get Jalali month names in English (for fallback).
     */
    fun getJalaliMonthNameEn(month: Int): String {
        val months = arrayOf(
            "", "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
            "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand"
        )
        return if (month in 1..12) months[month] else ""
    }
    
    /**
     * Persian weekday names, indexed by [persianWeekdayIndex] — so element 0 is
     * شنبه, not Monday.
     */
    private val PERSIAN_WEEKDAYS = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    /**
     * Position of [date] within the Iranian week, where Saturday is 0 and
     * Friday is 6.
     *
     * The Iranian week starts on Saturday. `java.time.DayOfWeek` numbers from
     * Monday = 1 (ISO), so any calendar grid or weekday list built directly
     * from `dayOfWeek.value` silently lands a Persian user on a Monday-first
     * week — one of the errors an Iranian user spots instantly.
     */
    fun persianWeekdayIndex(date: LocalDate): Int {
        // ISO: Mon=1 … Sat=6, Sun=7. Saturday must map to 0, so shift by two
        // and wrap: Sat(6)->0, Sun(7)->1, Mon(1)->2 … Fri(5)->6.
        return (date.dayOfWeek.value + 1) % 7
    }

    /** Persian name of the weekday [date] falls on. */
    fun getPersianWeekdayName(date: LocalDate): String =
        PERSIAN_WEEKDAYS[persianWeekdayIndex(date)]

    /**
     * Whether [date] falls on the Iranian weekend.
     *
     * Iran's weekend is Friday, with Thursday afternoon off in much of the
     * public sector. Only Friday is treated as the weekend here: this drives
     * whole-day shading in date views, and a Thursday is a normal working day
     * for the self-employed users this app is aimed at, so marking it off
     * would be wrong more often than right.
     */
    fun isIranianWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == java.time.DayOfWeek.FRIDAY

    /**
     * Format Jalali date for display.
     */
    fun formatJalaliDate(jalaliDate: JalaliDate, usePersianNames: Boolean = true): String {
        val monthName = if (usePersianNames) {
            getJalaliMonthName(jalaliDate.month)
        } else {
            getJalaliMonthNameEn(jalaliDate.month)
        }
        return "${jalaliDate.day} $monthName ${jalaliDate.year}"
    }
    
    // Internal conversion functions
    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        var g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = if (gm > 2) gy + 1 else gy
        var days = (355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1])
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += ((days - 1) / 365)
            days = (days - 1) % 365
        }
        var jm: Int
        var jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return JalaliDate(jy, jm, jd)
    }
    
    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var jy1 = if (jy > 979) jy - 979 else jy - 621
        var gy1 = if (jm < 7) 31 * (jm - 1) else (30 * (jm - 1)) + 6
        var gy2 = (365 * jy1) + ((jy1 / 33) * 8) + (((jy1 % 33) + 3) / 4) + 78 + jd + gy1
        var gy = 1600 + (400 * (gy2 / 146097))
        gy2 %= 146097
        if (gy2 > 36524) {
            gy += 100 * (--gy2 / 36524)
            gy2 %= 36524
            if (gy2 >= 365) gy2++
        }
        gy += 4 * (gy2 / 1461)
        gy2 %= 1461
        if (gy2 > 365) {
            gy += ((gy2 - 1) / 365)
            gy2 = (gy2 - 1) % 365
        }
        var gd = gy2 + 1
        var sal_a = intArrayOf(0, 31, if ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal_a[gm]) {
            gd -= sal_a[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }
}

