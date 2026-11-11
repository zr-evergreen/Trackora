package com.evergreen.trackora.recovery

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evergreen.trackora.data.local.database.DatabaseDamage
import com.evergreen.trackora.data.local.database.DatabaseRecheck
import com.evergreen.trackora.ui.recovery.RecoveryState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the recovery screen: checks the refused database again when asked, off
 * the main thread, and reports what came of it. It never opens the database;
 * [DatabaseRecheck] works on a copy.
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // Passed already when the refusal was lifted earlier in this process, and
    // this screen was opened again before the restart.
    private val _state = MutableStateFlow<RecoveryState>(
        if (DatabaseDamage.isRefused(context)) RecoveryState.Paused else RecoveryState.Passed
    )
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    /** The day the damage was recorded, in the phone's time zone, or null. */
    val recordedOn: LocalDate? = DatabaseDamage.recordedAt(context)
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    fun checkAgain() {
        if (_state.value == RecoveryState.Checking || _state.value == RecoveryState.Passed) return
        _state.value = RecoveryState.Checking
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { DatabaseRecheck(context).run() } }
            _state.value = outcome.fold(::recoveryStateFor) { e ->
                Log.e(TAG, "The database recheck could not finish", e)
                RecoveryState.NotPassed(RecoveryState.Why.NOT_COMPLETED)
            }
        }
    }

    private companion object {
        const val TAG = "TrackoraRecovery"
    }
}

/** What the recovery screen shows for each way the recheck can end. */
internal fun recoveryStateFor(outcome: DatabaseRecheck.Outcome): RecoveryState = when (outcome) {
    // No marker left means an earlier check already lifted the refusal: the
    // app only needs to restart, exactly as after this one.
    DatabaseRecheck.Outcome.NotRefused, is DatabaseRecheck.Outcome.Cleared -> RecoveryState.Passed
    is DatabaseRecheck.Outcome.StillRefused -> RecoveryState.NotPassed(RecoveryState.Why.STILL_DAMAGED)
    is DatabaseRecheck.Outcome.NotChecked -> RecoveryState.NotPassed(
        when (outcome.reason) {
            DatabaseRecheck.Reason.NOT_ENOUGH_SPACE -> RecoveryState.Why.NOT_ENOUGH_SPACE
            DatabaseRecheck.Reason.FILES_CHANGED -> RecoveryState.Why.FILES_CHANGED
            else -> RecoveryState.Why.NOT_COMPLETED
        }
    )
}
