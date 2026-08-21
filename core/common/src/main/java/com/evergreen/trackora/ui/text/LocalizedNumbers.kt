package com.evergreen.trackora.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.evergreen.trackora.util.PersianDigits

/**
 * Compose-side bridge to [PersianDigits].
 *
 * [PersianDigits] deliberately takes an explicit `usePersianDigits` flag rather
 * than reading the locale itself, so it stays a pure, trivially testable
 * function. That leaves every call site needing to answer "which locale am I
 * in?", and the wrong answer to that question — a hardcoded `true` — is exactly
 * the bug this whole effort exists to prevent, since it would push Persian
 * digits into the English build where they are simply wrong.
 *
 * These helpers answer it once, from the composition's configuration, so screens
 * never make the decision themselves.
 */

/**
 * Whether the active locale is Persian.
 *
 * The single place this question is answered, so digits, dates, month names and
 * calendar choice can never disagree about which locale is in effect.
 *
 * Keyed off the language subtag rather than the full locale, so `fa`, `fa-IR`
 * and `fa-AF` all agree. The check reads from the composition's configuration,
 * so it follows a runtime locale change without a process restart.
 */
@Composable
@ReadOnlyComposable
fun isPersianLocale(): Boolean =
    LocalConfiguration.current.locales[0].language == "fa"

/**
 * Formats a count or total for display, with thousands grouping.
 *
 * Use for quantities, entry counts and report totals — anything where a
 * four-digit value should read as `۱٬۲۳۴`.
 */
@Composable
@ReadOnlyComposable
fun localizedNumber(value: Int): String =
    PersianDigits.format(value, isPersianLocale())

/**
 * Formats a number that must carry no thousands grouping — years, day and
 * month numbers, version components.
 *
 * `۱۴۰۴` is a year; `۱٬۴۰۴` is a quantity that has been mistaken for one.
 */
@Composable
@ReadOnlyComposable
fun localizedNumberUngrouped(value: Int): String =
    PersianDigits.formatUngrouped(value, isPersianLocale())

/**
 * Converts any Western digits already embedded in [text] to match the locale.
 *
 * For strings that are assembled elsewhere and arrive pre-formatted — dates
 * built by [com.evergreen.trackora.util.JalaliCalendar], version names, values
 * interpolated into translated templates. Non-digit characters are preserved,
 * so separators and surrounding words survive intact.
 */
@Composable
@ReadOnlyComposable
fun localizedDigits(text: String): String =
    if (isPersianLocale()) PersianDigits.toPersian(text) else text
