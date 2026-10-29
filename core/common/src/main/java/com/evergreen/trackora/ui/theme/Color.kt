package com.evergreen.trackora.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Trackora's palette: a workshop ledger.
 *
 * The app replaces a paper notebook, so the neutrals are warm paper rather
 * than cold slate, and the dark theme is ink rather than navy. Three hues
 * carry meaning and nothing else gets colour:
 *
 *  - **Ink indigo** (primary) — the app's own actions and selection. It stays
 *    in the family of the blue launcher mark so the icon and the app read as
 *    one thing.
 *  - **Saffron** (tertiary) — finished work still waiting for its customer.
 *    That gap is where a pieceworker loses money, so it gets the one warm,
 *    attention-holding colour.
 *  - **Turquoise** (secondary) — delivered, done. Calm and closed.
 *
 * Saffron and turquoise are the two colours most associated with Persian
 * craft — the spice and the tilework — which gives the app a quiet identity
 * of its own without any ornament.
 *
 * Every text/background pair used by the app clears WCAG AA (4.5:1) in both
 * themes; the lowest is 6.1:1, saffron text on the light background.
 *
 * Every Material 3 role is defined, including the surface-container ladder.
 * Left unset, those fall back to the baseline purple-tinted defaults, which
 * is why some surfaces used to look faintly lilac.
 */

// ---- Light ----------------------------------------------------------------

internal val InkIndigo = Color(0xFF2B4ACB)
internal val InkIndigoContainer = Color(0xFFDEE3FF)
internal val OnInkIndigoContainer = Color(0xFF102A8C)

internal val Turquoise = Color(0xFF00696A)
internal val TurquoiseContainer = Color(0xFFC4EDE9)
internal val OnTurquoiseContainer = Color(0xFF00403F)

internal val Saffron = Color(0xFF8A5000)
internal val SaffronContainer = Color(0xFFFFE0B5)
internal val OnSaffronContainer = Color(0xFF4A2900)

internal val Paper = Color(0xFFFAF8F5)
internal val PaperInk = Color(0xFF1C1B1F)
internal val PaperInkVariant = Color(0xFF4B4950)
internal val PaperVariant = Color(0xFFE7E3DC)
internal val PaperContainerLowest = Color(0xFFFFFFFF)
internal val PaperContainerLow = Color(0xFFF5F2EE)
internal val PaperContainer = Color(0xFFEFECE7)
internal val PaperContainerHigh = Color(0xFFE9E6E1)
internal val PaperContainerHighest = Color(0xFFE3E0DB)
internal val PaperDim = Color(0xFFDCD9D4)
internal val PaperOutline = Color(0xFF7B7881)
internal val PaperOutlineVariant = Color(0xFFD3CEC7)

internal val ErrorRed = Color(0xFFBA1A1A)
internal val ErrorRedContainer = Color(0xFFFFDAD6)
internal val OnErrorRedContainer = Color(0xFF7D0E0E)

// ---- Dark -----------------------------------------------------------------

internal val InkIndigoDark = Color(0xFFB9C3FF)
internal val OnInkIndigoDark = Color(0xFF0F2786)
internal val InkIndigoContainerDark = Color(0xFF2B419F)
internal val OnInkIndigoContainerDark = Color(0xFFDEE3FF)

internal val TurquoiseDark = Color(0xFF7FD6D0)
internal val OnTurquoiseDark = Color(0xFF003735)
internal val TurquoiseContainerDark = Color(0xFF00504E)
internal val OnTurquoiseContainerDark = Color(0xFFA2F2EC)

internal val SaffronDark = Color(0xFFFFB866)
internal val OnSaffronDark = Color(0xFF4A2800)
internal val SaffronContainerDark = Color(0xFF6A3C00)
internal val OnSaffronContainerDark = Color(0xFFFFDDB5)

internal val Ink = Color(0xFF131317)
internal val InkText = Color(0xFFE5E1E6)
internal val InkTextVariant = Color(0xFFC8C5D0)
internal val InkVariant = Color(0xFF47464F)
internal val InkContainerLowest = Color(0xFF0E0E12)
internal val InkContainerLow = Color(0xFF1B1B20)
internal val InkContainer = Color(0xFF1F1F24)
internal val InkContainerHigh = Color(0xFF2A292F)
internal val InkContainerHighest = Color(0xFF35343A)
internal val InkBright = Color(0xFF3A383E)
internal val InkOutline = Color(0xFF928F9A)
internal val InkOutlineVariant = Color(0xFF47464F)

internal val ErrorRedDark = Color(0xFFFFB4AB)
internal val OnErrorRedDark = Color(0xFF690005)
internal val ErrorRedContainerDark = Color(0xFF93000A)
internal val OnErrorRedContainerDark = Color(0xFFFFDAD6)
