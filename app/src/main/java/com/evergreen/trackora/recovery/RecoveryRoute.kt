package com.evergreen.trackora.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.evergreen.trackora.ui.recovery.RecoveryScreen

/**
 * The recovery screen, wired to [RecoveryViewModel]. [onRestart] returns
 * false when the restart could not be asked for, and the screen then says how
 * to close and reopen the app.
 */
@Composable
fun RecoveryRoute(
    onRestart: () -> Boolean,
    viewModel: RecoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    RecoveryScreen(
        state = state,
        recordedOn = viewModel.recordedOn,
        onCheckAgain = viewModel::checkAgain,
        onRestart = { if (!onRestart()) viewModel.restartNotStarted() },
    )
}
