package com.evergreen.trackora.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes using @Serializable data classes.
 */
@Serializable
object TodayRoute

@Serializable
data class AddEditWorkRoute(
    val entryId: Long? = null
)

/**
 * The work history.
 *
 * @param initialStatus preselects a status filter, so arriving from Today's
 *   "see all waiting" lands on the undelivered set rather than on the whole
 *   log with the user left to reapply the filter they just expressed. Null
 *   from the bottom navigation, which means the whole history.
 */
@Serializable
data class AllWorkRoute(val initialStatus: String? = null)

@Serializable
object ReportsRoute

@Serializable
object SettingsRoute

