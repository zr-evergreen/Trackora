package com.evergreen.trackora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.evergreen.trackora.domain.model.Status

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
 * Mapped to what each status means rather than to a traffic light:
 *  - in progress is indigo, the app's own colour — work in hand;
 *  - completed is saffron — finished but still owed to a customer, the state
 *    the Today screen and the reminder exist to chase;
 *  - delivered is turquoise — handed over, closed.
 *
 * Containers are tinted rather than saturated so a Persian label sits on them
 * legibly; Persian has more strokes per glyph than Latin and loses more to a
 * busy background. Each pair clears 7.5:1.
 */
internal val LightStatusColors = TrackoraStatusColors(
    inProgressContainer = Color(0xFFE1E5FF),
    onInProgressContainer = Color(0xFF2A3A9A),
    completedContainer = Color(0xFFFFE6C2),
    onCompletedContainer = Color(0xFF6A3D00),
    deliveredContainer = Color(0xFFCDEEE9),
    onDeliveredContainer = Color(0xFF004F4C),
)

/**
 * Dark values.
 *
 * Not the light ones inverted: containers are deep and desaturated so they
 * read as surfaces rather than glowing, and the on-colours are lightened to
 * clear 7.4:1 against them. Light values on a dark ground would glow, which
 * is what made the summary tiles unreadable in dark mode before.
 */
internal val DarkStatusColors = TrackoraStatusColors(
    inProgressContainer = Color(0xFF262F5E),
    onInProgressContainer = Color(0xFFC3CBFF),
    completedContainer = Color(0xFF47300E),
    onCompletedContainer = Color(0xFFFFC77A),
    deliveredContainer = Color(0xFF103F3B),
    onDeliveredContainer = Color(0xFF8FDCD5),
)

/** Container colour for [status]. */
fun TrackoraStatusColors.container(status: Status): Color = when (status) {
    Status.IN_PROGRESS -> inProgressContainer
    Status.COMPLETED -> completedContainer
    Status.DELIVERED -> deliveredContainer
}

/** Content colour that goes on [container] for [status]. */
fun TrackoraStatusColors.content(status: Status): Color = when (status) {
    Status.IN_PROGRESS -> onInProgressContainer
    Status.COMPLETED -> onCompletedContainer
    Status.DELIVERED -> onDeliveredContainer
}

/**
 * The colour that marks [status] on its own, with no container behind it:
 * the edge mark of an entry card, its status label, and the icon of the
 * button that moves an entry into it.
 *
 * These are the scheme's three accents, one per stage — indigo while the
 * work is under way, saffron once it is finished and waiting, turquoise once
 * it has reached the customer — so a card's status reads in the same colour
 * as the Today band and the filter chips that already use them. Each clears
 * 6:1 against the card in both themes, so they are safe for text.
 */
@Composable
@ReadOnlyComposable
fun statusAccent(status: Status): Color = when (status) {
    Status.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    Status.COMPLETED -> MaterialTheme.colorScheme.tertiary
    Status.DELIVERED -> MaterialTheme.colorScheme.secondary
}

internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Status colours for the current theme. */
val MaterialTheme.statusColors: TrackoraStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
