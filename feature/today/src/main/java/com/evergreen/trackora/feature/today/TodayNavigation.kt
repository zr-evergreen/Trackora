package com.evergreen.trackora.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.navigation.TodayRoute
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState

/**
 * Navigation graph for Today feature.
 */
fun NavGraphBuilder.todayNavigation(
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    onSeeAllUndelivered: () -> Unit = {},
    onEntryClick: (Long) -> Unit
) {
    composable<TodayRoute> {
        TodayScreen(
            snackbarHostState = snackbarHostState,
            viewModel = hiltViewModel(),
            contentPadding = contentPadding,
            onSeeAllUndelivered = onSeeAllUndelivered,
            onEntryClick = onEntryClick
        )
    }
}

