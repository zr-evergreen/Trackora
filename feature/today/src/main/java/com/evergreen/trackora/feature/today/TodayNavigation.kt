package com.evergreen.trackora.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.navigation.TodayRoute
import androidx.compose.foundation.layout.PaddingValues

/**
 * Navigation graph for Today feature.
 */
fun NavGraphBuilder.todayNavigation(
    contentPadding: PaddingValues,
    onEntryClick: (Long) -> Unit
) {
    composable<TodayRoute> {
        TodayScreen(
            viewModel = hiltViewModel(),
            contentPadding = contentPadding,
            onEntryClick = onEntryClick
        )
    }
}

