package com.evergreen.trackora.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Professional, calm Material 3 color palette for the Work Statistics app.
 *
 * Design goals:
 * - Neutral, trustworthy blue/teal accents
 * - Soft neutrals for backgrounds (no pure white / pure black)
 * - High contrast and comfortable for long daily use
 * - Explicit colors for status indicators
 */

// Primary actions – calm blue
val PrimaryLight = Color(0xFF1565C0)          // Blue 700-ish
val OnPrimaryLight = Color(0xFFE3F2FD)
val PrimaryContainerLight = Color(0xFFD0E3FF)
val OnPrimaryContainerLight = Color(0xFF05315F)

val PrimaryDark = Color(0xFF90CAF9)
val OnPrimaryDark = Color(0xFF00315F)
val PrimaryContainerDark = Color(0xFF0D47A1)
val OnPrimaryContainerDark = Color(0xFFE3F2FD)

// Secondary actions – muted teal
val SecondaryLight = Color(0xFF00897B)
val OnSecondaryLight = Color(0xFFE0F2F1)
val SecondaryContainerLight = Color(0xFFB2DFDB)
val OnSecondaryContainerLight = Color(0xFF004D40)

val SecondaryDark = Color(0xFF80CBC4)
val OnSecondaryDark = Color(0xFF003631)
val SecondaryContainerDark = Color(0xFF004D40)
val OnSecondaryContainerDark = Color(0xFFB2DFDB)

// Tertiary – soft desaturated orange for subtle highlights (not primary actions)
val TertiaryLight = Color(0xFFFB8C00)
val OnTertiaryLight = Color(0xFFFFF3E0)
val TertiaryContainerLight = Color(0xFFFFE0B2)
val OnTertiaryContainerLight = Color(0xFF5F2C00)

val TertiaryDark = Color(0xFFFFCC80)
val OnTertiaryDark = Color(0xFF442100)
val TertiaryContainerDark = Color(0xFF5F2C00)
val OnTertiaryContainerDark = Color(0xFFFFF3E0)

// Neutrals – calm backgrounds and surfaces
val BackgroundLight = Color(0xFFF4F5F7)
val SurfaceLight = Color(0xFFFAFAFA)
val OnBackgroundLight = Color(0xFF121212)
val OnSurfaceLight = Color(0xFF1E1E1E)

// Dark neutrals are slightly warm (a hint of brown) to avoid a harsh, blue-black feel.
val BackgroundDark = Color(0xFF121013)   // near-black with a warm tint
val SurfaceDark = Color(0xFF1C191E)      // warm charcoal
val OnBackgroundDark = Color(0xFFE5E1E6)
val OnSurfaceDark = Color(0xFFE5E1E6)

// Outline / dividers
val OutlineLight = Color(0xFFB0BEC5)
val OutlineDark = Color(0xFF4A4A55)

// Status colors (used for chips, pills, and indicators)
// Tuned for emotional tone: calm progress, reassuring completion, confident delivery.
val StatusInProgress = Color(0xFF546E7A)  // Blue-grey: calm, neutral progress
val StatusCompleted = Color(0xFF2E7D32)   // Reassuring green: tasks safely done
val StatusDelivered = Color(0xFF00695C)   // Deep teal: confident, reliable delivery

