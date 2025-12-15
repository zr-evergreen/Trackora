package com.evergreen.trackora.feature.reports

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.evergreen.trackora.navigation.ReportsRoute
import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Navigation graph for Reports feature.
 */
fun NavGraphBuilder.reportsNavigation(
    contentPadding: PaddingValues
) {
    composable<ReportsRoute> {
        ReportsScreen(
            contentPadding = contentPadding,
            viewModel = hiltViewModel()
        )
    }
}

