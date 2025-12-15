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

@Serializable
object AllWorkRoute

@Serializable
object ReportsRoute

