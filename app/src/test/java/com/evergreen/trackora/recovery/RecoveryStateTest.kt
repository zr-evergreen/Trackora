package com.evergreen.trackora.recovery

import com.evergreen.trackora.data.local.database.DatabaseRecheck.Outcome
import com.evergreen.trackora.data.local.database.DatabaseRecheck.Reason
import com.evergreen.trackora.ui.recovery.RecoveryState
import com.evergreen.trackora.ui.recovery.RecoveryState.Why
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * What the recovery screen shows for each way a recheck ends. Only a lifted
 * refusal offers a restart; every other outcome keeps the screen, with the
 * check offered again.
 */
class RecoveryStateTest {

    @Test
    fun aLiftedRefusalOffersARestart() {
        assertEquals(RecoveryState.Passed, recoveryStateFor(Outcome.Cleared(File("copies/1-cleared"), 2)))
        // Lifted by an earlier check whose restart did not happen.
        assertEquals(RecoveryState.Passed, recoveryStateFor(Outcome.NotRefused))
    }

    @Test
    fun aDatabaseStillRefusedSaysSoWhateverTheReason() {
        Reason.entries.forEach { reason ->
            assertEquals(reason.name, RecoveryState.NotPassed(Why.STILL_DAMAGED), recoveryStateFor(Outcome.StillRefused(reason, "")))
        }
    }

    @Test
    fun anUnfinishedCheckSaysWhyWhenItCanBeHelped() {
        val expected = Reason.entries.associateWith { reason ->
            when (reason) {
                Reason.NOT_ENOUGH_SPACE -> Why.NOT_ENOUGH_SPACE
                Reason.FILES_CHANGED -> Why.FILES_CHANGED
                else -> Why.NOT_COMPLETED
            }
        }
        expected.forEach { (reason, why) ->
            assertEquals(reason.name, RecoveryState.NotPassed(why), recoveryStateFor(Outcome.NotChecked(reason, "")))
        }
    }
}
