package com.evergreen.trackora.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.evergreen.trackora.common.R

/**
 * Vazirmatn, the app's typeface for both locales.
 *
 * Vazirmatn is the de facto standard Persian UI face and is what Iranian users
 * are used to reading on a phone. Roboto renders Persian correctly but a
 * Persian reader clocks it as foreign immediately — the letterforms are drawn
 * by someone solving a Latin problem first.
 *
 * It is used for English too rather than only under `values-fa`. Vazirmatn's
 * Latin glyphs are a full, competent design (it is derived from DejaVu), so a
 * single family across both locales keeps one visual identity and avoids the
 * jarring shift you get when a screen mixes Persian and Latin text — which
 * this app does constantly, since entry titles are free text.
 *
 * ### Why four static weights instead of the variable font
 *
 * Vazirmatn ships a variable `Vazirmatn[wght].ttf` that would be one file
 * instead of four. But selecting a weight along a variable axis needs
 * `FontVariation`, which is API 26+, and this app's `minSdk` is 24. On API 24
 * and 25 the variable font would resolve to a single default instance, so
 * every weight in the type scale would render identically and the hierarchy
 * would collapse — on precisely the old devices the desugaring work exists to
 * support.
 *
 * Four static weights cost about 488 KB before compression. That is the price
 * of type hierarchy working on Android 7, and it is worth paying.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)
