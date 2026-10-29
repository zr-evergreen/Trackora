package com.evergreen.trackora.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Marks a style as carrying text the user typed, whose language the app does
 * not control.
 *
 * Every other string in the app is one we wrote and translated, so its
 * direction matches the layout by definition. Entry titles and descriptions do
 * not: a Persian user records "پیراهن" one day and "Nike order 42" the next,
 * and both live in the same list.
 *
 * With the paragraph direction inherited from the layout, a Latin title in the
 * Persian build is laid out right-to-left. The letters stay in order — the
 * shaping engine handles that — but anything without an inherent direction
 * migrates to the wrong end: a trailing full stop jumps to the left of the
 * sentence, and a title like "Order 42:" renders as ":Order 42". Neutrals at
 * the boundary are precisely what bidi resolution decides using the paragraph
 * direction, so the fix is to stop imposing one.
 *
 * [TextDirection.Content] derives the paragraph direction from the first strong
 * character of the text itself, so each entry is laid out in its own language
 * and mixed titles resolve the way the Unicode bidi algorithm intends.
 */
fun TextStyle.forUserContent(): TextStyle =
    copy(textDirection = TextDirection.Content)

/**
 * Alignment that puts user content against the layout's start edge, whatever
 * its own direction.
 *
 * [forUserContent] decides how a title's characters are ordered; this decides
 * which edge the line sits against, and the two are separate questions. Left
 * to its own direction, a Latin title in the Persian build sat against the
 * left edge, beside the action button, while Persian titles sat on the right,
 * so a list of mixed titles had no common edge to read down. Aligned to the
 * layout, every title starts at the same edge as the rest of the card, and
 * each one is still ordered correctly within its line.
 */
@Composable
@ReadOnlyComposable
fun layoutStartAlign(): TextAlign =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left
