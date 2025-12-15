package com.evergreen.trackora.feature.allwork

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.navigation.AllWorkRoute

/**
 * Navigation graph for All Work feature.
 */
fun NavGraphBuilder.allWorkNavigation() {
    composable<AllWorkRoute> {
        AllWorkScreen(
            onNavigateBack = {
                // Navigation handled by bottom nav
            }
        )
    }
}

