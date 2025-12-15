package com.evergreen.trackora.feature.allwork

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.navigation.AllWorkRoute
import androidx.compose.foundation.layout.PaddingValues

/**
 * Navigation graph for All Work feature.
 */
fun NavGraphBuilder.allWorkNavigation(
    contentPadding: PaddingValues,
    onEntryClick: (Long) -> Unit
) {
    composable<AllWorkRoute> {
        AllWorkScreen(
            contentPadding = contentPadding,
            onEntryClick = onEntryClick,
            viewModel = hiltViewModel()
        )
    }
}

