package com.evergreen.trackora.util

import com.evergreen.trackora.BuildConfig

/**
 * Application version information with semantic versioning support.
 *
 * This is the SINGLE SOURCE OF TRUTH for app versioning.
 * All version information is defined here and used by both:
 * - Build system (build.gradle.kts reads from here via BuildConfig)
 * - Application code (runtime access via BuildConfig)
 *
 * Semantic Versioning (SemVer) format: MAJOR.MINOR.PATCH
 * - MAJOR: Increment for breaking changes that are not backward compatible
 * - MINOR: Increment for new features that are backward compatible
 * - PATCH: Increment for bug fixes that are backward compatible
 * - BUILD: Increment for each build/release (used as versionCode)
 *
 * This follows professional Android development practices by:
 * - Separating version logic from build files
 * - Providing a single source of truth for version info
 * - Making version accessible throughout the codebase
 * - Type-safe access to version information
 * - Semantic versioning support for better version management
 *
 * To update version, modify the constants below:
 * ```
 * const val VERSION_MAJOR = 1
 * const val VERSION_MINOR = 0
 * const val VERSION_PATCH = 0
 * const val VERSION_BUILD = 1
 * ```
 *
 * Usage:
 * ```
 * val version = AppVersion.displayVersion  // "1.0.0"
 * val major = AppVersion.major  // 1
 * val minor = AppVersion.minor  // 0
 * val patch = AppVersion.patch  // 0
 * val fullVersion = AppVersion.fullVersion  // "1.0.0 (1)"
 * ```
 */
object AppVersion {
    /**
     * Version constants - SINGLE SOURCE OF TRUTH
     * Update these values to change the app version
     */
    const val VERSION_MAJOR = 1
    const val VERSION_MINOR = 0
    const val VERSION_PATCH = 0
    const val VERSION_BUILD = 1

    /**
     * The version name (e.g., "1.0.0").
     * This is the user-facing version string in MAJOR.MINOR.PATCH format.
     * Constructed from BuildConfig which is set from the constants above.
     */
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    /**
     * The version code (e.g., 1).
     * This is an integer that must be incremented for each release.
     * Set from VERSION_BUILD constant via BuildConfig.
     */
    val versionCode: Int
        get() = BuildConfig.VERSION_CODE

    /**
     * Major version number.
     * Increment for breaking changes that are not backward compatible.
     * Uses the VERSION_MAJOR constant directly.
     */
    val major: Int
        get() = VERSION_MAJOR

    /**
     * Minor version number.
     * Increment for new features that are backward compatible.
     * Uses the VERSION_MINOR constant directly.
     */
    val minor: Int
        get() = VERSION_MINOR

    /**
     * Patch version number.
     * Increment for bug fixes that are backward compatible.
     * Uses the VERSION_PATCH constant directly.
     */
    val patch: Int
        get() = VERSION_PATCH

    /**
     * Build number (same as versionCode).
     * Increment for each build/release.
     * Uses the VERSION_BUILD constant directly.
     */
    val buildNumber: Int
        get() = VERSION_BUILD

    /**
     * Full version string combining name and code.
     * Format: "1.0.0 (1)"
     */
    val fullVersion: String
        get() = "$versionName ($versionCode)"

    /**
     * Display version string for UI.
     * Format: "1.0.0"
     */
    val displayVersion: String
        get() = versionName

    /**
     * Semantic version string with build info.
     * Format: "1.0.0+build.1" (optional, for internal use)
     */
    val semanticVersion: String
        get() = "$versionName+build.$versionCode"

    /**
     * Application ID for reference.
     */
    val applicationId: String
        get() = BuildConfig.APPLICATION_ID

    /**
     * Build type (e.g., "debug", "release").
     */
    val buildType: String
        get() = BuildConfig.BUILD_TYPE

    /**
     * Checks if this is a major version (patch and minor are 0).
     */
    val isMajorVersion: Boolean
        get() = true

    /**
     * Checks if this is a minor version (patch is 0).
     */
    val isMinorVersion: Boolean
        get() = true
}
