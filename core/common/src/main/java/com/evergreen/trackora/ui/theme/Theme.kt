package com.evergreen.trackora.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = InkIndigo,
    onPrimary = Color.White,
    primaryContainer = InkIndigoContainer,
    onPrimaryContainer = OnInkIndigoContainer,
    inversePrimary = InkIndigoDark,
    secondary = Turquoise,
    onSecondary = Color.White,
    secondaryContainer = TurquoiseContainer,
    onSecondaryContainer = OnTurquoiseContainer,
    tertiary = Saffron,
    onTertiary = Color.White,
    tertiaryContainer = SaffronContainer,
    onTertiaryContainer = OnSaffronContainer,
    background = Paper,
    onBackground = PaperInk,
    surface = Paper,
    onSurface = PaperInk,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = PaperInkVariant,
    surfaceTint = InkIndigo,
    inverseSurface = Color(0xFF31302F),
    inverseOnSurface = Color(0xFFF4F0EC),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorRedContainer,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    scrim = Color.Black,
    surfaceBright = Paper,
    surfaceDim = PaperDim,
    surfaceContainerLowest = PaperContainerLowest,
    surfaceContainerLow = PaperContainerLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,
)

private val DarkColors = darkColorScheme(
    primary = InkIndigoDark,
    onPrimary = OnInkIndigoDark,
    primaryContainer = InkIndigoContainerDark,
    onPrimaryContainer = OnInkIndigoContainerDark,
    inversePrimary = InkIndigo,
    secondary = TurquoiseDark,
    onSecondary = OnTurquoiseDark,
    secondaryContainer = TurquoiseContainerDark,
    onSecondaryContainer = OnTurquoiseContainerDark,
    tertiary = SaffronDark,
    onTertiary = OnSaffronDark,
    tertiaryContainer = SaffronContainerDark,
    onTertiaryContainer = OnSaffronContainerDark,
    background = Ink,
    onBackground = InkText,
    surface = Ink,
    onSurface = InkText,
    surfaceVariant = InkVariant,
    onSurfaceVariant = InkTextVariant,
    surfaceTint = InkIndigoDark,
    inverseSurface = InkText,
    inverseOnSurface = Color(0xFF313034),
    error = ErrorRedDark,
    onError = OnErrorRedDark,
    errorContainer = ErrorRedContainerDark,
    onErrorContainer = OnErrorRedContainerDark,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    scrim = Color.Black,
    surfaceBright = InkBright,
    surfaceDim = Ink,
    surfaceContainerLowest = InkContainerLowest,
    surfaceContainerLow = InkContainerLow,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = InkContainerHigh,
    surfaceContainerHighest = InkContainerHighest,
)

/**
 * One radius scale for everything. Cards and fields share `medium` so a form
 * and the list it feeds look like one object; sheets and dialogs take
 * `extraLarge`. Buttons and chips keep Material's own full and small shapes.
 */
private val TrackoraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Fill for cards that sit on the page: white paper on the warm background in
 * light, one step lighter than the background in dark — a card darker than
 * the page would read as a hole rather than a sheet.
 */
val ColorScheme.cardContainer: Color
    get() = if (background.luminance() < 0.5f) surfaceContainerLow else surfaceContainerLowest

@Composable
fun TrackoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            controller.isAppearanceLightStatusBars = !darkTheme
            // Every screen ends in a bar — navigation or Save — that draws its
            // own colour behind the system navigation bar. The translucent
            // scrim Android adds over three-button navigation turned that
            // strip a different shade from the bar above it, so it is turned
            // off and the buttons are tinted for the theme instead. Before 10
            // the bar cannot be see-through, so it takes the bar colour.
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.navigationBarColor = Color.Transparent.toArgb()
                controller.isAppearanceLightNavigationBars = !darkTheme
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = colorScheme.surfaceContainer.toArgb()
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = TrackoraShapes,
            content = content
        )
    }
}
