package com.evergreen.trackora.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.common.R
import com.evergreen.trackora.ui.text.localizedDate
import com.evergreen.trackora.ui.theme.TrackoraIcons
import java.time.LocalDate

/** Where the recovery screen is, while the app's database is refused. */
sealed interface RecoveryState {
    /** Refused, and nothing has been tried yet. */
    data object Paused : RecoveryState

    /** The check is running. */
    data object Checking : RecoveryState

    /** The check passed and the refusal was lifted; the app must restart to open the database. */
    data object Passed : RecoveryState

    /** The check did not pass, or could not finish. The database is still refused. */
    data class NotPassed(val why: Why) : RecoveryState

    enum class Why { STILL_DAMAGED, NOT_ENOUGH_SPACE, FILES_CHANGED, NOT_COMPLETED }
}

const val RecoveryScreenTag = "recovery_screen"
const val RecoveryCheckButtonTag = "recovery_check"
const val RecoveryRestartButtonTag = "recovery_restart"

/**
 * Shown instead of the whole app while the database is refused: what has
 * happened, that nothing was deleted, and a way to check the database again.
 * Nothing else in the app can work without its database, so nothing else is
 * reachable from here.
 */
@Composable
fun RecoveryScreen(
    state: RecoveryState,
    recordedOn: LocalDate?,
    onCheckAgain: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag(RecoveryScreenTag),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = TrackoraIcons.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.recovery_body_paused), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.recovery_body_kept), style = MaterialTheme.typography.bodyLarge)
        if (recordedOn != null) {
            Text(
                text = stringResource(R.string.recovery_recorded_on, localizedDate(recordedOn)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))

        when (state) {
            RecoveryState.Passed -> {
                Text(
                    text = stringResource(R.string.recovery_passed_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.recovery_passed_body), style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth().testTag(RecoveryRestartButtonTag),
                ) {
                    Text(stringResource(R.string.recovery_restart))
                }
                Text(
                    text = stringResource(R.string.recovery_restart_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                if (state is RecoveryState.NotPassed) {
                    Text(
                        text = stringResource(state.why.message()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val checking = state == RecoveryState.Checking
                Button(
                    onClick = onCheckAgain,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth().testTag(RecoveryCheckButtonTag),
                ) {
                    if (checking) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.recovery_checking))
                        }
                    } else {
                        Text(stringResource(R.string.recovery_check_again))
                    }
                }
                Text(
                    text = stringResource(R.string.recovery_check_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun RecoveryState.Why.message(): Int = when (this) {
    RecoveryState.Why.STILL_DAMAGED -> R.string.recovery_failed_damaged
    RecoveryState.Why.NOT_ENOUGH_SPACE -> R.string.recovery_failed_space
    RecoveryState.Why.FILES_CHANGED -> R.string.recovery_failed_changed
    RecoveryState.Why.NOT_COMPLETED -> R.string.recovery_failed_other
}
