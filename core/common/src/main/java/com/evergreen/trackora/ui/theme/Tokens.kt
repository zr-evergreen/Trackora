package com.evergreen.trackora.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.dp

/**
 * Spacing on a 4dp grid. Screens use [screen] at their edges and [section]
 * between groups; everything inside a card steps down from [md].
 */
object TrackoraSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Horizontal inset of every screen. */
    val screen = 16.dp

    /** Gap between groups on a screen. */
    val section = 24.dp

    /** Space kept clear at the end of a list so the add button never covers its last row. */
    val fabClearance = 96.dp
}

/**
 * Motion is short and purposeful: it explains where something came from or
 * went, never decorates. Durations follow Material 3's short/medium bands so
 * nothing outlasts a tap, and every animation in the app takes its timing
 * from here, which keeps the feel consistent.
 *
 * Compose scales these by the system's animator duration setting, so a user
 * who turns animations off gets none.
 */
object TrackoraMotion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 350

    /** Entering: fast out of the gate, settling gently. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaving: gathers speed and goes. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Changes in place. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
