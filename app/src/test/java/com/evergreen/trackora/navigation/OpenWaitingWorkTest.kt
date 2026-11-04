package com.evergreen.trackora.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import com.evergreen.trackora.domain.model.Status
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [openWaitingWork], which opens All Work filtered to Completed from
 * Today's band and from the waiting card in Reports.
 *
 * It has to be a switch to the All Work tab rather than a screen pushed on top
 * of the tab it came from. Pushed, All Work became part of that tab's saved
 * history, and tapping Today or Reports brought All Work back instead.
 */
class OpenWaitingWorkTest {

    private val startId = 42
    private val waitingWork = AllWorkRoute(initialStatus = Status.COMPLETED.name)

    private val nav = mockk<NavController>(relaxed = true)

    /** Opens the waiting work and returns the options it navigated with. */
    private fun open(): NavOptions {
        val options = slot<NavOptions>()
        nav.openWaitingWork(startDestinationId = startId)
        verify(exactly = 1) { nav.navigate(waitingWork, capture(options), null as Navigator.Extras?) }
        return options.captured
    }

    @Test
    fun `opens All Work filtered to completed work`() {
        open()
    }

    @Test
    fun `leaves the tab it came from the way the bottom bar does, keeping its history`() {
        val options = open()

        assertEquals(startId, options.popUpToId)
        assertFalse(options.isPopUpToInclusive())
        assertTrue(options.shouldPopUpToSaveState())
    }

    @Test
    fun `opens All Work fresh, so the filter it is given applies`() {
        val options = open()

        assertFalse(options.shouldRestoreState())
        verifyOrder {
            nav.clearBackStack<AllWorkRoute>()
            nav.navigate(waitingWork, any<NavOptions>(), null as Navigator.Extras?)
        }
    }

    @Test
    fun `a second tap does not stack another All Work`() {
        assertTrue(open().shouldLaunchSingleTop())
    }
}
