package com.evergreen.trackora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours for the three work statuses.
 *
 * Material's scheme has no slot for "in progress" or "delivered", so these
 * previously lived as `Color(0xFFFF9800)` and friends, repeated six times
 * across two feature modules as chip text, chip background, border and pill
 * fill. They were Material 2 palette values belonging to no theme, and none of
 * them had been checked for contrast in dark mode.
 *
 * Defining them as a theme extension keeps them one edit away from each other
 * and lets the dark scheme differ properly rather than reusing light values on
 * a dark ground.
 *
 * Each status is a container/on-container pair rather than a single hue,
 * because a badge needs both and picking the text colour at each call site is
 * how contrast bugs get in.
 */
data class TrackoraStatusColors(
    val inProgressContainer: Color,
    val onInProgressContainer: Color,
    val completedContainer: Color,
    val onCompletedContainer: Color,
    val deliveredContainer: Color,
    val onDeliveredContainer: Color,
)

/**
 * Light values.
 *
 * Amber for in progress, green for completed, blue for delivered — the
 * associations the app already used, kept so the change is a consolidation
 * rather than a re-skin. Containers are tinted rather than saturated so a
 * Persian label sits on them legibly; Persian has more strokes per glyph than
 * Latin and loses more to a busy background.
 */
internal val LightStatusColors = TrackoraStatusColors(
    inProgressContainer = Color(0xFFFFF0D6),
    onInProgressContainer = Color(0xFF7A4A00),
    completedContainer = Color(0xFFDCF2E3),
    onCompletedContainer = Color(0xFF15603A),
    deliveredContainer = Color(0xFFDDE9FC),
    onDeliveredContainer = Color(0xFF14457F),
)

/**
 * Dark values.
 *
 * Not the light ones inverted: containers are deep and desaturated so they
 * read as surfaces rather than glowing, and the on-colours are lightened to
 * clear 4.5:1 against them. The old literals were the same in both themes,
 * which is what made the summary tiles unreadable in dark mode before.
 */
internal val DarkStatusColors = TrackoraStatusColors(
    inProgressContainer = Color(0xFF3A2A0E),
    onInProgressContainer = Color(0xFFF5C46A),
    completedContainer = Color(0xFF12301F),
    onCompletedContainer = Color(0xFF7FD3A3),
    deliveredContainer = Color(0xFF14243D),
    onDeliveredContainer = Color(0xFF9DC2F5),
)

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Status colours for the current theme. */
val MaterialTheme.statusColors: TrackoraStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
