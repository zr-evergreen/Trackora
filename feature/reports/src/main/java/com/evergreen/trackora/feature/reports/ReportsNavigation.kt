package com.evergreen.trackora.feature.reports

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.evergreen.trackora.navigation.ReportsRoute

/**
 * Navigation graph for Reports feature.
 */
fun NavGraphBuilder.reportsNavigation() {
    composable<ReportsRoute> {
        ReportsScreen()
    }
}

