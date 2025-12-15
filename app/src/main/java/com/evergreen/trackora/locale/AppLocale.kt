package com.evergreen.trackora.locale

/**
 * Supported application locales.
 * SYSTEM means follow device default.
 */
enum class AppLocale(val languageTag: String?) {
    SYSTEM(null),
    EN("en"),
    FA("fa");
}

