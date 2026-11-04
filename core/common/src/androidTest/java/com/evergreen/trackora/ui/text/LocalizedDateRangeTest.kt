package com.evergreen.trackora.ui.text

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * Tests for [localizedDateRange], the period line under the Reports title.
 *
 * A range names its month and year once unless it crosses them, in the
 * calendar the reader uses: the same seven days are "Oct 23 – 29, 2026" in
 * English and fall wholly within Aban in Persian, while thirty days that end
 * in Aban begin in Mehr.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedDateRangeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun range(start: LocalDate, end: LocalDate, locale: Locale): String {
        var text = ""
        composeRule.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                text = localizedDateRange(start, end)
            }
        }
        composeRule.waitForIdle()
        return text
    }

    @Test
    fun aWeekWithinOneMonthNamesTheMonthOnce() {
        assertEquals(
            "Oct 23 – 29, 2026",
            range(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 29), Locale.US)
        )
    }

    @Test
    fun aRangeAcrossMonthsNamesBoth() {
        assertEquals(
            "Sep 30 – Oct 29, 2026",
            range(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 29), Locale.US)
        )
    }

    @Test
    fun persianRangesAreJalaliWithPersianDigits() {
        // 23–29 October 2026 is 1–7 Aban 1405.
        assertEquals(
            "۱ – ۷ آبان ۱۴۰۵",
            range(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 29), Locale("fa"))
        )
    }

    @Test
    fun aPersianRangeAcrossJalaliMonthsNamesBoth() {
        // 30 September 2026 is 8 Mehr; 29 October is 7 Aban.
        assertEquals(
            "۸ مهر – ۷ آبان ۱۴۰۵",
            range(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 29), Locale("fa"))
        )
    }
}
