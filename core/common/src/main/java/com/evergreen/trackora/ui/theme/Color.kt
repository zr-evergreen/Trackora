package com.evergreen.trackora.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Professional, modern Material 3 color palette for Trackora.
 *
 * Design goals:
 * - Modern, trustworthy blue primary color with excellent contrast
 * - Soft, comfortable backgrounds that reduce eye strain
 * - High accessibility (WCAG AA+ compliant)
 * - Clear visual hierarchy and status indicators
 * - Professional appearance suitable for business use
 */

// Primary actions – modern professional blue
val PrimaryLight = Color(0xFF2563EB)          // Modern blue-600, vibrant yet professional
val OnPrimaryLight = Color(0xFFFFFFFF)        // Pure white for maximum contrast
val PrimaryContainerLight = Color(0xFFDBEAFE) // Light blue container
val OnPrimaryContainerLight = Color(0xFF1E3A8A) // Dark blue text on container

val PrimaryDark = Color(0xFF93C5FD)          // Light blue for dark theme
val OnPrimaryDark = Color(0xFF1E3A8A)         // Dark blue text
val PrimaryContainerDark = Color(0xFF1E40AF)  // Dark blue container
val OnPrimaryContainerDark = Color(0xFFDBEAFE) // Light text on container

// Secondary actions – sophisticated teal
val SecondaryLight = Color(0xFF0D9488)        // Modern teal-600
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCCFBF1) // Light teal container
val OnSecondaryContainerLight = Color(0xFF134E4A) // Dark teal text

val SecondaryDark = Color(0xFF5EEAD4)        // Light teal for dark theme
val OnSecondaryDark = Color(0xFF134E4A)
val SecondaryContainerDark = Color(0xFF0F766E) // Dark teal container
val OnSecondaryContainerDark = Color(0xFFCCFBF1)

// Tertiary – warm accent for highlights and emphasis
val TertiaryLight = Color(0xFFF59E0B)        // Amber-500 for warmth
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFEF3C7) // Light amber container
val OnTertiaryContainerLight = Color(0xFF78350F) // Dark amber text

val TertiaryDark = Color(0xFFFCD34D)         // Light amber for dark theme
val OnTertiaryDark = Color(0xFF78350F)
val TertiaryContainerDark = Color(0xFFD97706)  // Dark amber container
val OnTertiaryContainerDark = Color(0xFFFEF3C7)

// Neutrals – comfortable, easy on the eyes
val BackgroundLight = Color(0xFFF8FAFC)       // Very light gray-blue (slate-50)
val SurfaceLight = Color(0xFFFFFFFF)          // Pure white for cards
val SurfaceVariantLight = Color(0xFFF1F5F9)    // Slightly darker for subtle elevation
val OnBackgroundLight = Color(0xFF0F172A)      // Dark slate for text
val OnSurfaceLight = Color(0xFF1E293B)         // Slightly lighter for surface text
val OnSurfaceVariantLight = Color(0xFF475569)  // Medium gray for secondary text

// Dark theme – warm, comfortable dark tones
val BackgroundDark = Color(0xFF0F172A)        // Dark slate-900
val SurfaceDark = Color(0xFF1E293B)           // Slate-800 for cards
val SurfaceVariantDark = Color(0xFF334155)     // Slate-700 for elevated surfaces
val OnBackgroundDark = Color(0xFFF1F5F9)      // Light slate for text
val OnSurfaceDark = Color(0xFFF8FAFC)         // Very light for surface text
val OnSurfaceVariantDark = Color(0xFFCBD5E1)   // Medium light for secondary text

// Outline / dividers – subtle but visible
val OutlineLight = Color(0xFFE2E8F0)          // Light slate border
val OutlineVariantLight = Color(0xFFF1F5F9)   // Very light for subtle dividers
val OutlineDark = Color(0xFF475569)          // Medium slate for dark theme
val OutlineVariantDark = Color(0xFF334155)    // Darker for subtle dividers

// Error colors – clear and accessible
val ErrorLight = Color(0xFFDC2626)            // Red-600
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)   // Light red container
val OnErrorContainerLight = Color(0xFF991B1B) // Dark red text

val ErrorDark = Color(0xFFEF4444)            // Red-500 for dark theme
val OnErrorDark = Color(0xFFFFFFFF)
val ErrorContainerDark = Color(0xFF991B1B)
val OnErrorContainerDark = Color(0xFFFEE2E2)

// Status colors – clear, professional indicators
val StatusInProgress = Color(0xFF3B82F6)     // Blue-500: active, in progress
val StatusCompleted = Color(0xFF10B981)      // Green-500: success, completed
val StatusDelivered = Color(0xFF0D9488)      // Teal-600: delivered, confirmed
val StatusWarning = Color(0xFFF59E0B)        // Amber-500: warning, attention needed
val StatusError = Color(0xFFDC2626)          // Red-600: error, failed

