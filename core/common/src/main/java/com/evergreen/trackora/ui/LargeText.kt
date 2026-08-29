package com.evergreen.trackora.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Whether the user has turned text up far enough that side-by-side layouts
 * should stack instead.
 *
 * The alternative fixes are both wrong. Shrinking the text defeats the setting
 * the user deliberately changed; shrinking the control pushes it under the
 * 48dp touch target. Stacking keeps type and targets at full size and spends
 * vertical space, which is the one resource a phone has plenty of.
 *
 * 1.3 is the threshold because Android's own "large" step is 1.3 and its
 * "largest" is 1.5; below that, rows still fit at 320dp.
 *
 * Read from the composition, so it follows a live change to the system setting
 * rather than needing a restart.
 */
@Composable
@ReadOnlyComposable
fun isLargeTextScale(threshold: Float = 1.3f): Boolean =
    LocalConfiguration.current.fontScale >= threshold
