package com.evergreen.trackora.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.ui.recovery.RecoveryScreen

/** The recovery screen, wired to [RecoveryViewModel]. */
@Composable
fun RecoveryRoute(
    onRestart: () -> Unit,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    RecoveryScreen(
        state = state,
        recordedOn = viewModel.recordedOn,
        onCheckAgain = viewModel::checkAgain,
        onRestart = onRestart,
    )
}
