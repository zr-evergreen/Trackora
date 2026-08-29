package com.evergreen.trackora.feature.allwork

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.toRoute
import com.evergreen.trackora.domain.model.Status
import com.evergreen.trackora.navigation.AllWorkRoute
import androidx.compose.foundation.layout.PaddingValues

/**
 * Navigation graph for All Work feature.
 */
fun NavGraphBuilder.allWorkNavigation(
    contentPadding: PaddingValues,
    onEntryClick: (Long) -> Unit
) {
    composable<AllWorkRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<AllWorkRoute>()
        AllWorkScreen(
            contentPadding = contentPadding,
            onEntryClick = onEntryClick,
            initialStatus = route.initialStatus?.let { runCatching { Status.valueOf(it) }.getOrNull() },
            viewModel = hiltViewModel()
        )
    }
}

