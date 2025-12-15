package com.evergreen.trackora.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.navigation.TodayRoute

/**
 * Navigation graph for Today feature.
 */
fun NavGraphBuilder.todayNavigation(
    onAddEntryClick: () -> Unit
) {
    composable<TodayRoute> {
        TodayScreen(
            viewModel = hiltViewModel(),
            onAddEntryClick = onAddEntryClick
        )
    }
}

