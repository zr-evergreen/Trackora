package com.evergreen.trackora.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale for Trackora, tuned for Persian first and Latin second.
 *
 * Three deliberate departures from the Material 3 defaults:
 *
 * **1. `letterSpacing` is 0 on every style.** Material's scale applies positive
 * tracking to most styles (up to 0.5.sp on `bodyLarge` and the label styles)
 * and negative tracking to `displayLarge`. Both are wrong for Persian, and not
 * as a matter of taste: Persian is a connected script, so tracking pushes apart
 * letters that are supposed to join and pulls apart the ones already joined.
 * The result reads as broken rather than merely loose. Latin loses a little
 * optical polish at small sizes from zeroing it; a single scale serving the
 * primary locale correctly is the better trade.
 *
 * **2. Line heights are roughly 15–20% taller.** Persian sits on a deeper
 * vertical band than Latin — descenders like ی and ج drop further, and
 * diacritics sit above the cap line. At Material's default ratios the
 * ascenders and descenders of adjacent lines collide, and at `bodySmall` the
 * glyphs clip against the line box outright.
 *
 * **3. The smallest sizes are raised.** Persian carries more strokes per glyph
 * than Latin at the same point size, so it loses legibility faster as size
 * drops. `bodySmall` goes 12→13.sp and `labelSmall` 11→12.sp. Both are read
 * rather than merely glanced at in this app — `bodySmall` carries entry
 * descriptions and `labelSmall` carries status chips.
 */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 76.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 61.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 47.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 37.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
)
